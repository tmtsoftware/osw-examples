//* CmdTool.cpp: Command Interface Test Tool */

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
 *       void MainLoop()  -- infinite loop reading standard input, sending
 *                           and receiving data from the sequencer.
 *       int SendCmd()    -- send CmdStr to server connected to fd.
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
void MainLoop(int msgFd);
int ProcessMsg(int msgFd);
int ProcessCmdLine(int msgFd);
int SendCmd(int fd, char *cmdStr);

//
// Global Variables
//
static char hostname[256] = "localhost";
static bool debug = false;  // printout messages received from server
static bool quiet = false;  // turn off input prompt.

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
        case 'q':
            quiet = true;
            break;
        default:
            cerr << "Usage: CmdTool [-d] [-h hostname] [-s server] [-p port]" << endl;
            exit(-1);
        }
    }

    // establish connection to Command Server
    int msgFd = net_connect(server, hostname, pname, BLOCKING);
    if (msgFd < 0) {
        cerr << "CmdTool: Could not connect to server: error: " << NET_ERRSTR(msgFd) << endl;
        exit(msgFd);
    }

    MainLoop(msgFd);

    net_close(msgFd);

    exit(0);
}

void MainLoop(int msgFd)
{
    bool quitting = false;     // clean up and exit

    while (!quitting) {
        fd_set readfds;        // file descriptor list to check for reading
        int n;

        if (!quiet)
            cerr << hostname << " > ";

        FD_ZERO(&readfds);
        FD_SET(msgFd, &readfds);
        FD_SET(STDIN_FILENO, &readfds);

        // wait for input on one of file descriptors
        n = select(FD_SETSIZE, &readfds, (fd_set *)0, (fd_set *)0, (struct timeval *)0);

        if (n <= 0) {
            cerr << "CmdTool: Call to select() failed. " << strerror(errno) << endl;
            return;
        }

        // check if message received from command server
        if (FD_ISSET(msgFd, &readfds)) {
            if (ProcessMsg(msgFd) <= 0) {
                quitting = true;
                break;
            }
        }

        // check if user has entered input from command line
        if (FD_ISSET(STDIN_FILENO, &readfds)) {
            if (ProcessCmdLine(msgFd) < 0) {
                quitting = true;
                break;
            }
        }
    }

    do {
        // clean up pending messages from server
     } while (ProcessMsg(msgFd) > 0);

}

int ProcessMsg(int msgFd)
{
    char buf[NET_MAX_MSG_LEN];

    int len = net_recv(msgFd, (char *)buf, NET_MAX_MSG_LEN, BLOCKING);
    if (len < 0) {
        cerr << "CmdTool: net_recv() error:" << NET_ERRSTR(len)
             << ", errno=" << errno << strerror(errno) << endl;
        return len;
    }
    else if (len == NEOF) {
        cerr << "CmdTool: Connection broken..." << endl;
        return len;
    }

    RspMsg *rspMsg = (RspMsg *)buf;

    if (rspMsg->hdr.msgId == RSP_TYPE) {
        if (debug)
            cerr << rspMsg;
        else
            cerr << rspMsg->rsp << endl;
    }
    else {
        if (debug)
            cerr << "Received: " << rspMsg->hdr << "... ignoring" << endl;
    }

    return len;
}

int ProcessCmdLine(int msgFd)
{
    static char lastCmd[MAX_CMD_LEN] = "\0";
    char cmdLine[MAX_CMD_LEN] = "\0";

    cin.getline(cmdLine, sizeof(cmdLine));

    if ((strcasecmp(cmdLine, "quit") == 0)
        || (cin.eof())) {
        return -1;
    }
    else if (strncmp("!!", cmdLine, 2) == 0) {
        strcpy(cmdLine, lastCmd);
    }
    else {
        strcpy(lastCmd, cmdLine);
    }

    int nBytes = SendCmd(msgFd, cmdLine);

    if (nBytes < 0) {
        cerr << "CmdTool: net_send() error:" << NET_ERRSTR(nBytes)
             << ", errno=" << errno << strerror(errno) << endl;
    }

    return nBytes;
};

int SendCmd(int fd, char *cmdStr)
{
    CmdMsg msg = { { CMD_TYPE, ANY_TASK, sizeof(msg), 0 }, "\0" };
    static uint16_t seqNo = 0;         //!< Command sequence number.

    assert(fd > 0);
    assert(cmdStr != NULL);

    msg.hdr.seqNo = ++seqNo;
    strncpy(msg.cmd, cmdStr, sizeof(msg.cmd) - 1);

    return net_send(fd, (char *)&msg, sizeof(msg), BLOCKING);
}
