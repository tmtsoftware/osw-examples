/* PrintMsg.cpp: Command Interface Test Tool */

/* Copyright (c) 2018-2026, Jet Propulsion Laboratory */

#ifdef MODULE_HDR
/* ***************************************************************************
 *
 * Project:      Thirty Meter Telescope (TMT)
 * System:       Primary Mirror Control System (M1CS)
 * Task:         Lower Segment Box
 * Module:       PrintMsg.cpp
 * Title:        Print functions for M1CS Message definitions.
 * ----------------------------------------------------------------------------
 * Revision History:
 *
 *   Date           By               Description
 *
 *  09-22-21       gbrack            Initial Delivery (for M1CS project)
 * ----------------------------------------------------------------------------
 *
 * Description:
 *
 *************************************************************************** */
#endif

#include "PrintMsg.h"

ostream &operator<<(ostream &out, const MsgHdr &hdr)
{
    out << endl << "MsgHdr = { " << hex << uppercase 
        << "0x" << hdr.msgId << "," << dec << hdr.srcId << "," 
        << hdr.msgLen << "," << hdr.seqNo << " };" << endl;

    return out;
}

ostream &operator<<(ostream &out, const MsgHdr *hdr)
{
    out << *hdr;
    return out;
}

ostream &operator<<(ostream &out, const CmdMsg &msg)
{
    out << msg.hdr;
    out << "Cmd = \"" << msg.cmd << "\"" << endl;
    
    return out;
}

ostream &operator<<(ostream &out, const CmdMsg *msg)
{
    out << *msg;
    return out;
}

ostream &operator<<(ostream &out, const RspMsg &msg)
{
    out << msg.hdr;
    out << "Resp = \"" << msg.rsp << "\"" << endl;

    return out;
}

ostream &operator<<(ostream &out, const RspMsg *msg)
{
    out << *msg;
    return out;
}

ostream &operator<<(ostream &out, const TimeTag &t)
{
    out << "TimeTag = { " << t.tv_sec << "," << t.tv_nsec << " };" << endl;
    return out;
}

ostream &operator<<(ostream &out, const TimeTag *t)
{
    out << *t;
    return out;
}

ostream &operator<<(ostream &out, const DataHdr &hdr)
{
    out << hdr.hdr << hdr.time << endl;
    return out;
}

ostream &operator<<(ostream &out, const DataHdr *hdr)
{
    out << *hdr;
    return out;
}

ostream &operator<<(ostream &out, const LogMsg &msg)
{
    out << msg.hdr;
    out << static_cast<underlying_type<LogLevel>::type>(msg.level);

    out << "msg = \"" << msg.message << "\"" << endl;
    return out;
}

ostream &operator<<(ostream &out, const LogMsg *msg)
{
    out << *msg;
    return out;
}

ostream &operator<<(ostream &out, const RawDataMsg &msg)
{
    out << msg.hdr;
    return out;
}

ostream &operator<<(ostream &out, const RawDataMsg *msg)
{
    out << *msg;
    return out;
}
