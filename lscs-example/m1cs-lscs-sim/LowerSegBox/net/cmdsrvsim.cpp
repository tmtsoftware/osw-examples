/* CmdSrvSim.c -- Net Services Command Server */

/**
 *****************************************************************************
 *
 * @file CmdSrvSim.c
 *      GLC Net Services Test Command Server.
 *
 * @par Project
 *      TMT Primary Mirror Control System (M1CS) \n
 *      Jet Propulsion Laboratory, Pasadena, CA
 *
 * @author      Thang Trinh
 *              Gary Brack
 * @date        15-Sep-2021 -- Initial delivery.
 *              1-Oct-2021 -- Initial release.
 *
 * Copyright (c) 2015-2022, California Institute of Technology
 *
 *****************************************************************************/


#include "GlcMsg.h"
#include "net_glc.h"
#include <errno.h>
#include <iostream>
#include <thread>
#include <chrono>
#include <mutex>
#include <stdlib.h>
#include <time.h>
#include <assert.h>
#include <stdbool.h>
#include <string.h>

using namespace std;

#define MAXCLIENTS (492)
#define MAXMSGLEN  (1024)

static int listenfd = ERROR;
static int cli_fd[MAXCLIENTS];
static bool debug = false;
mutex sendLock;          //  Semaphone to allow single thread to send at a time

void EventLoop();
int ProcessMessage(int sockfd);
void ProcessCommand(int sockfd, CmdMsg *msg);
int SendResponse(int sockfd, char *response, int csn);

int main(int argc, char **argv)
{
    char server[128] = LSEB_CMD_SRV;
    int i;

    while ((i = getopt(argc, argv, "s:d-")) != -1) {
        switch (i) {
        case 's':
            strncpy(server, optarg, sizeof(server) - 1);
            break;
        case 'd':
            debug = true;
            break;
        default:
            cerr << "Usage: CmdSrvSim [-d] [-s server]" << endl;
            exit(-1);
        }
    }

    //  Seed random number generator in preparation to simulate
    //  processing a message to form a response.
    srand(time(NULL));

    for (i = 0; i < MAXCLIENTS; i++)
        cli_fd[i] = ERROR;

    // initialize server's network connection
    if ((listenfd = net_init(server)) < 0) {
        cerr << "CmdSrvSim: net_init() error: " << NET_ERRSTR(listenfd)
             << ", errno=" << errno << endl;
        exit(listenfd);
    }
    else {
        cout << "CmdSrvSim: Listening on socket " << listenfd << "..." << endl;
    }

    // Main event loop
    EventLoop();

    if (listenfd != ERROR)
        net_close(listenfd);

    for (i = 0; i < MAXCLIENTS; i++)
        if (cli_fd[i] != ERROR)
            net_close(cli_fd[i]);

    return 0;
}

void EventLoop()
{
    fd_set read_fds;     // file descriptors to be polled
    int nfds;
    int sockfd;
    int i;

    while (1) {

        FD_ZERO(&read_fds);
        FD_SET(listenfd, &read_fds);

        for (i = 0; i < MAXCLIENTS; i++) {
            if (cli_fd[i] != ERROR)
                FD_SET(cli_fd[i], &read_fds);
        }

        nfds = select(FD_SETSIZE, &read_fds, (fd_set *)0, (fd_set *)0, (struct timeval *)0);
        if (nfds <= 0) {
            if (errno == EINTR) {
                continue;
            }
            // error on select
            cerr << "CmdSrvSim: select() error:" << strerror(errno) << endl;
            exit(-1);
        }
        else {
            if (FD_ISSET(listenfd, &read_fds)) {

                // accept new client connection
                if ((sockfd = net_accept(listenfd, BLOCKING)) < 0) {
                    cerr << "CmdSrvSim: net_accept() error: " << NET_ERRSTR(sockfd)
                         << ", errno=" << errno << endl;
                    net_close(listenfd);
                    exit(sockfd);
                }
                cout << "CmdSrvSim: Connection accepted..." << endl;

                int n = 0;

                while ((n < MAXCLIENTS) && (cli_fd[n] != ERROR))
                    n++;

                if (n < MAXCLIENTS)
                    cli_fd[n] = sockfd;
                else {
                    cerr << "CmdSrvSim: Max client connections exceeded." << endl;
                    net_close(sockfd);
                }

                if (--nfds <= 0)
                    continue;
            }

            for (i = 0; i < MAXCLIENTS; i++)
                if (cli_fd[i] != ERROR && FD_ISSET(cli_fd[i], &read_fds)) {

                    // service client's request
                    (void)ProcessMessage(i);
                    if (--nfds <= 0)
                        break;
                }
        }
    }
}

int ProcessMessage(int index)
{
    // FIXME: added dynamic malloc that is freed in lamda thread as quick proof of concept.
    //        Need to restructure to correct this approach.
    char *msg = (char *)malloc(MAXMSGLEN);

    if(msg == NULL) {
        cerr << "CmdSrvSim: out of memory" << endl;
        assert(msg != NULL);
    }

    int len = net_recv(cli_fd[index], msg, MAXMSGLEN, BLOCKING);

    if (len < 0) {
        cerr << "CmdSrvSim: net_recv() error: " << NET_ERRSTR(len)
             << ", errno=" << errno << endl;
        free (msg);
        return len;
    }
    else if (len == NEOF) {
        cout << "CmdSrvSim: Closing broken connection..." << endl;
        net_close(cli_fd[index]);
        cli_fd[index] = ERROR;
        free (msg);
        return len;
    }

    if (((MsgHdr *)msg)->msgId == CMD_TYPE)
        ProcessCommand(cli_fd[index], (CmdMsg *)msg);
    else
        cerr << "CmdSrvSim: Invalid message received." << endl;

    return len;
}

void ProcessCommand(int sockfd, CmdMsg *msg)
{
    auto lambda = [](int lambda_sockfd, CmdMsg *lambda_msg)
    {
        char cmdName[MAX_CMD_LEN] = {};
        char rsp[MAX_CMD_LEN] = {};

        lambda_msg->cmd[MAX_CMD_LEN - 1] = '\0';  // truncate command.
        if (debug)
            cout << "cmd= \"" << lambda_msg->cmd << "\"" << endl;

        (void)sscanf(lambda_msg->cmd, "%s", cmdName);
        (void)snprintf(rsp, sizeof(rsp), "%.240s: Completed.", cmdName);

        //  This simulates code that does the work of processing
        //  a message.
        this_thread::sleep_for(chrono::seconds(rand() % 5));

        SendResponse(lambda_sockfd, rsp, lambda_msg->hdr.seqNo);

        free(lambda_msg);
    };

    thread cmdThread(lambda, sockfd, msg);

    cmdThread.detach();

    return;
}


int SendResponse(int sockfd, char *response, int csn)
{
    RspMsg rsp_msg = { { RSP_TYPE, LSEB_CMD_TASK, sizeof(RspMsg), 0 }, "" };

    rsp_msg.hdr.seqNo = csn;

    strncpy(rsp_msg.rsp, response, sizeof(rsp_msg.rsp)-1);

    // lock net_send() to protect writes from multiple threads.
    const std::lock_guard<std::mutex> lock(sendLock);

    int status = net_send(sockfd, (char *)&rsp_msg, sizeof rsp_msg, BLOCKING);

    if (status <= 0) {
        cerr << "CmdSrvSim: net_send() error: " << NET_ERRSTR(status)
             << ", errno=" << errno << endl;
    }

    return status;
}
