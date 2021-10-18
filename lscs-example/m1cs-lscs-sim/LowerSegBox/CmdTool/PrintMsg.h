/**
 *****************************************************************************
 *
 * @file PrintMsg.h
 *     Print functions for GLC Messages
 *
 *
 * @par Project
 *     TMT Primary Mirror Control System (M1CS) \n
 *     Jet Propulsion Laboratory, Pasadena, CA
 *
 * @author      Gary Brack
 * @date       22-Sept-2021 -- Initial delivery.
 *
 * Copyright (c) 2018-2026, California Institute of Technology
 *
 ****************************************************************************/

#ifndef PRINTMSG_H_
#define PRINTMSG_H_

#include "GlcLscsIf.h"

#include <ostream>

using namespace std;

ostream &operator<<(ostream &out, const MsgHdr &hdr);
ostream &operator<<(ostream &out, const MsgHdr *hdr);

ostream &operator<<(ostream &out, const CmdMsg &msg);
ostream &operator<<(ostream &out, const CmdMsg *msg);

ostream &operator<<(ostream &out, const RspMsg &msg);
ostream &operator<<(ostream &out, const RspMsg *msg);

ostream &operator<<(ostream &out, const TimeTag &t);
ostream &operator<<(ostream &out, const TimeTag *t);

ostream &operator<<(ostream &out, const DataHdr &hdr);
ostream &operator<<(ostream &out, const DataHdr *hdr);

ostream &operator<<(ostream &out, const LogMsg &msg);
ostream &operator<<(ostream &out, const LogMsg *msg);

ostream &operator<<(ostream &out, const RawDataMsg &msg);
ostream &operator<<(ostream &out, const RawDataMsg *msg);

#endif /* PRINTMSG_H_ */
