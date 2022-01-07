#
# Project:	Thirty Meter Telescope (TMT)
# System:	Primary Mirror Control System (M1CS)
# Module:	Lower Segment Box
# Prog:		Command Tool (CmdTool)
#
# Author:	G. Brack, JPL, July 2021
#

TARGET_SYS=
ifeq ($(TARGET_SYS),_am335x)
    TOOLCHAIN_PREFIX=arm-none-linux-gnueabihf-
else ifeq ($(TARGET_SYS),_am64x)
    TOOLCHAIN_PREFIX=aarch64-none-linux-gnu-
else
    TOOLCHAIN_PREFIX=
endif

# override default compilier tools to cross compile 
CPP = $(TOOLCHAIN_PREFIX)gcc -E
CC = $(TOOLCHAIN_PREFIX)gcc 
CXX = $(TOOLCHAIN_PREFIX)g++ 
LD = $(TOOLCHAIN_PREFIX)g++
AR = $(TOOLCHAIN_PREFIX)ar
RANLIB = $(TOOLCHAIN_PREFIX)ranlib
STRIP = $(TOOLCHAIN_PREFIX)strip
OBJCOPY = $(TOOLCHAIN_PREFIX)objcopy
OBJDUMP = $(TOOLCHAIN_PREFIX)objdump
AS = $(TOOLCHAIN_PREFIX)as
AR = $(TOOLCHAIN_PREFIX)ar
NM = $(TOOLCHAIN_PREFIX)nm
GDB = $(TOOLCHAIN_PREFIX)gdb

# DEFINES: list of preprocessor flags to set when compiling source
DEFINES = -DLINUX -DPARSE_CMD -I ../ParserLib
DEFINES = -DLINUX 

# CFLAGS: standard C compilier flags
CFLAGS = -Wall -Werror -g -O -fPIC -std=c11

# CXXFLAGS: standard C++ compilier flags to set
CXXFLAGS = -Wall -Werror -g -O -fPIC 

# LLIBS: local project libraries to linked to EXE.
LLIBS = -lnet$(TARGET_SYS) -lparser$(TARGET_SYS)
LLIBS = -lnet$(TARGET_SYS) 

# LDLIBS: system libraries/library paths to linked to EXE
LDLIBS = -lpthread

# EXES: name of executable(s) to be created.
EXES = CmdTool$(TARGET_SYS) CmdSrvSim$(TARGET_SYS)

# SRCS: list of source files to be compiled/linked with EXE.o 
SRCS = PrintMsg.cpp

# LIB: Name of library to be created.
LIB = 

# LIB_SRCS: list of source files to be compiled and linked into LIB
LIB_SRCS =  

