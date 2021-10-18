/* CmdTool.cpp: Command Interface Test Tool */

/* Copyright (c) 1995-2010,2015, Jet Propulsion Laboratory */

#ifdef MODULE_HDR
/* ***************************************************************************
 *
 * Project:      Thirty Meter Telescope (TMT)
 * System:       Primary Mirror Control System (M1CS)
 * Task:         Lower Segment Box
 * Module:       CmdTool.cpp
 * Title:        LSEB Command Interface Test Tool
 * ----------------------------------------------------------------------------
 * Revision History:
 *
 *   Date           By               Description
 *
 *  08-20-05       gbrack            Initial Delivery (for SCDU project)
 *  07-01-21       gbrack            Modified for testing M1CS LSEB Interface
 * ----------------------------------------------------------------------------
 *
 * Description:
 *       This is a simple demo program to test sending commands over the
 *       network to the LSEB's command interface. This program allows the user
 *       to enter commands from the command line, then formats them into
 *       the correct CmdMsg structure and sends it to the command processor.
 *       It then receives a RspMsg back from the command processor and
 *       displays the result.
 *
 * Functions Defined:
 *       void main()      -- main program (initializes connection to sequencer
 *       void main_loop() -- infinite loop reading standard input, sending
 *                           and receiving data from the sequencer.
 *       int send_cmd()   -- send CmdStr to server connected to fd.
 *************************************************************************** */
#endif

#include "GlcLscsIf.h"
#include "PrintMsg.h"
#include "net.h"
#include "net_glc.h"
#include <assert.h>
#include <errno.h>
#include <iostream>
#include <stdbool.h>
#include <string.h>

//
// Function Definitions
//
void main_loop(int msgFd);
int send_cmd(int fd, char *cmdStr, char *errStr);

//
// Global Variables
//
static char hostname[256] = "localhost";
static bool debug = true;

int main(int argc, char *argv[])
{
    static char server[256] = LSEB_CMD_SRV;
    static int pname = LSEB_CMD_TASK;
    int c;

    while ((c = getopt(argc, argv, "h:s:p:d-")) != -1) {
        switch (c) {
        case 'h':
            strncpy(hostname, optarg, sizeof(hostname) - 1);
            break;
        case 's':
            strncpy(server, optarg, sizeof(server) - 1);
            break;
        case 'p':
            pname = atoi(optarg);
            break;
        case 'd':
            debug = true;
            break;
        default:
            cerr << "Usage: CmdTool [-d] [-h hostname] [-s server] [-p port]" << endl;
            exit(-1);
        }
    }

    /* establish connection to Command Server */
    int msgFd = net_connect(server, hostname, pname, BLOCKING);
    if (msgFd < 0) {
        cerr << "CmdTool: Could not connect to server: error: " << NET_ERRSTR(msgFd) << endl;
        exit(msgFd);
    }

    main_loop(msgFd);

    net_close(msgFd);

    exit(0);
}

void main_loop(int msgFd)
{
    RspMsg *rspMsg = NULL; // message buffer to read into.

    assert (msgFd>=0);

    try {
        // Allocate buffer space to receive max message size.
        rspMsg = (RspMsg *)new char[NET_MAX_MSG_LEN];
    }
    catch (bad_alloc &) {
        cerr << "CmdTool: Failed to allocate message buffer" << endl;
        return;
    }

    for (;;) {
        fd_set readfds;        // file descriptor list to check for reading
        bool quitting = false; // clean up and exit
        bool prompt = true;    // show hostname of server connection.
        int n;

        if (prompt)
            cerr << hostname << " > ";

        FD_ZERO(&readfds);

        FD_SET(msgFd, &readfds);

        if (!quitting)
            FD_SET(0, &readfds);

        /* wait for input from one of file descriptors */
        n = select(FD_SETSIZE, &readfds, (fd_set *)0, (fd_set *)0, (struct timeval *)0);

        if (n <= 0) {
            cerr << "Call to select() failed. " << strerror(errno) << endl;
            break;
        }

        /* check if data received from command server */
        if (FD_ISSET(msgFd, &readfds)) {
            int len = net_recv(msgFd, (char *)rspMsg, NET_MAX_MSG_LEN, BLOCKING);
            if (len < 0) {
                cerr << "CmdTool: net_recv() error:" << NET_ERRSTR(len) << ", errno=" << errno << strerror(errno)
                     << endl;
                break;
            }
            else if (len == NEOF) {
                cerr << "Connection broken...\n";
                break;
            }

            if (rspMsg->hdr.msgId != RSP_TYPE) {
                if (debug)
                    cerr << "Received: " << rspMsg->hdr << "... ignoring" << endl;
            }
            else {
                if (debug)
                    cerr << rspMsg;
                else
                    cerr << *rspMsg->rsp << endl;
            }

            if (quitting)
                break;
        }

        /* check if user has entered input from command line */
        if (FD_ISSET(0, &readfds)) {
            char errMsg[MAX_CMD_LEN] = "\0";
            char cmdLine[MAX_CMD_LEN] = "\0";
            char lastCmd[MAX_CMD_LEN] = "\0";

            cin.getline(cmdLine, sizeof(cmdLine));

            if (strncasecmp(cmdLine, "quit", 4) == 0) {
                quitting = true;
                break;
            }
            else if (strncmp("!!", cmdLine, 2) == 0) {
                strcpy(cmdLine, lastCmd);
            }
            else {
                strcpy(lastCmd, cmdLine);
            }

            int status = send_cmd(msgFd, cmdLine, errMsg);

            if (status < 0) {
                cerr << errMsg << endl;
                continue;
            }
        }
    }

    delete[] (char *)rspMsg;
}

int send_cmd(int fd, char *cmdStr, char *errStr)
{
    CmdMsg msg = { { CMD_TYPE, ANY_TASK, sizeof(msg), 0 }, "\0" };
    static uint16_t seqNo = 0; //!< Command sequence number.

    assert(errStr != NULL);
    assert(cmdStr != NULL);
    assert(fd > 0);

    msg.hdr.seqNo = ++seqNo;
    strncpy(msg.cmd, cmdStr, sizeof(msg.cmd) - 1);

    int status = net_send(fd, (char *)&msg, sizeof(msg), BLOCKING);
    if (status < 0) {
        snprintf(errStr, MAX_CMD_LEN, "CmdTool: net_send() errStr: %s, errno=%d\n", NET_ERRSTR(status), errno);
    }
    else {
        strcpy(errStr, "");
        status = SUCCESS;
    }

    return status;
}
