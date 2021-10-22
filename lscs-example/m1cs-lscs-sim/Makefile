#
#
# Project:		Thirty Meter Telescope (TMT)
# System:		Primary Mirror Control System (M1CS)
# Module:		Global Makefile - Adapted from SCDU Project
# Authors:		T. Trinh, JPL, June 2021
#			G. Brack, JPL, Apr 1995
#			T. Truong, JPL, Nov 1993; T. Trinh, JPL, Oct 1992
#
# Usage:
#             
#   		From any source directory:
#		make -f ../../Makefile
#
#   		Remove object files:
#		make -f ../../Makefile clean
#		make -f ../../Makefile xclean
#
# Notes: 
#	This makefile requires GNU make.
#
# Discription:
#	The global makefile is designed to work from any of the subdirectories
#       within the M1CS system.  By default it defines flags that would be
#       correct for Linux.  The makefile looks for 2 files in the current
#	directory when compiling, Include.make, and .depend.make.  When the
#	makefile is processed by make it first defines all of the global
#	macros.  Then it looks at Include.make to override any of the
# 	global macros.  After processing Include.make it returns to this
#	file to define the global compilation macros used to create
#	object modules, and executables.  Finally if .depend.make exists
#	make processes it to determine which files should be recompiled.
#	So when finished the makefile could be thought of as a single file
#	that looks like:
#
#		---------------------------------------
#		|                                     |
#		|  Global Execution Commands          |
#		|                                     |
#		---------------------------------------
#		|                                     |
#		|  Include.make in current directory  |
#		|                                     |
#		---------------------------------------
#		|                                     |
#		|  Global Compilation Commands        |
#		|                                     |
#		---------------------------------------
#		|                                     |
#		|  .depend.make                       |
#		|                                     |
#		---------------------------------------
#
#


# tell make directories to search for header file dependencies
vpath %.h ../include
vpath %.h ../../include
# tell make directories to search for library dependencies 
vpath %.a ../lib
vpath %.a ../../lib

ifeq ($(ROOTDIR),)
    ROOTDIR:=$(dir $(abspath $(MAKEFILE_LIST)))
endif

# Global directories
BINDIR= ${ROOTDIR}bin
LIBDIR= ${ROOTDIR}lib
BUILDDIR=.build

# Execution Commands
RM = /bin/rm -f
OS:= $(shell uname)
ifeq ($(OS),SunOS)
    MAKE= /usr/local/bin/make -f ${ROOTDIR}/Makefile
    LINT= /opt/SUNWspro/bin/lint
    YACC= /usr/ccs/bin/yacc
    LEX= /usr/ccs/bin/lex
    INSTALL= /usr/local/bin/install
    RANLIB=
    CPP=/usr/ccs/lib/cpp
    LD= /opt/SUNWspro/bin/cc
    AR= /usr/ccs/bin/ar
    AS= /usr/ccs/bin/as
    CC= /opt/SUNWspro/bin/cc
    CXX= /opt/SUNWspro/bin/CC
else
    MAKE= /usr/bin/make -f ${ROOTDIR}/Makefile
    LINT=				# Currently don't have lint on Linux
    YACC= /usr/bin/bison
    LEX= /usr/bin/flex
    INSTALL= /bin/cp
    RANLIB=
    CPP=/usr/bin/cpp
    LD= /usr/bin/cc
    AR= /usr/bin/ar
    AS= /usr/bin/as
    CC= /usr/bin/cc
    CXX= /usr/bin/CC
endif

INCLUDE=.
DEFINES= -DSVR4
YFLAGS= -d
CFLAGS= -g 
LDFLAGS= -L../lib -L$(LIBDIR)

DEPFLAGS = -MT $@ -MMD -MP -MF $(BUILDDIR)/$*.d
CPPFLAGS = $(DEPFLAGS) $(DEFINES) -I$(INCLUDE) -I../include -I../../include 
LINTFLAGS= -u -Ncheck=macro -Nlevel=4

-include Include.make

.l.c:
	$(LEX) -o $@ $<
.y.c:
	$(YACC.y) -o $@ $< 

# Explicitly undefine default rule to build EXES.o file (for TARGET_SYS)
%:%.o

# Variables to simplify make rules logic below.
_EXES = $(EXES:%=$(BUILDDIR)/%)
_OBJS = $(filter %.o,$(SRCS:%.c=$(BUILDDIR)/%.o)) $(filter %.o,$(SRCS:%.cpp=$(BUILDDIR)/%.o)) $(filter %.o,$(SRCS:%.s=$(BUILDDIR)/%.o))
_LIB = $(LIB:%=$(BUILDDIR)/lib%.a)
_SHLIB = $(SHLIB:%=$(BUILDDIR)/%.so)
_LIB_OBJS = $(filter %.o,$(LIB_SRCS:%.c=$(BUILDDIR)/%.o)) $(filter %.o,$(LIB_SRCS:%.cpp=$(BUILDDIR)/%.o)) $(filter %.o,$(LIB_SRCS:%.s=$(BUILDDIR)/%.o))
DEPFILES = $(filter %.d,$(_LIB_OBJS:%.o=%.d)) $(filter %.d,$(_OBJS:%.o=%.d)) $(_EXES:%$(TARGET_SYS)=%.d)

# Global Compilation Macros
all: $(BUILDDIR) $(LIB:%=$(LIBDIR)/lib%.a) $(SHLIB:%=$(LIBDIR)/%.so) $(EXES:%=$(BINDIR)/%)
	@(D="$(SUBDIRS)";                                		\
	for i in $$D; do (						\
	    if [ -d $$i ]; then cd $$i;					\
	        if [ -e ./build.sh ]; then ./build.sh $@ $$i;		\
	        else $(MAKE) $@; fi;					\
	    else echo "Warning: No such directory: $$i"; break; fi);	\
	done)

# Install libary in $(LIBDIR)
$(LIB:%=$(LIBDIR)/lib%.a) : $(_LIB)
	@(if [ -d ../lib ]; then $(INSTALL) -f -p $? ../lib; fi )
	$(INSTALL) -f -p $? $(LIBDIR)

# Install shared libary in $(LIBDIR)
$(SHLIB:%=$(LIBDIR)/%.so) : $(_SHLIB)
	@(if [ -d ../lib ]; then $(INSTALL) -f -p $? ../lib; fi )
	$(INSTALL) -f -p $? $(LIBDIR)

# Install application(s) in $(BINDIR)
$(EXES:%=$(BINDIR)/%): $(_EXES) 
	@(E="$(EXES)";								\
	for i in $$E; do ( 							\
	    $(INSTALL) -f -p $(BUILDDIR)/$$i $(BINDIR)/;			\
	    (if [ -d ../bin ];then $(INSTALL) -f -p $(BUILDDIR)/$$i ../bin/;fi) \
	);									\
	done)					

# Link applications(s) in $(BUILDDIR)
$(_EXES): $(ROOTDIR)/Makefile Include.make $(LLIBS:-l%=lib%.a) 
$(_EXES): $(_OBJS) $(_EXES:%$(TARGET_SYS)=%.o) 
#	echo "EXE_OBJ=["$(_EXES:%$(TARGET_SYS)=%.o)"]"; echo "@=[$(@)];"; echo "%=[$(%)]"; echo "^=[$(^)]"; echo "?=[$(?)]";
	$(LD) $(@:%$(TARGET_SYS)=%.o) $(filter-out $(_EXES:%$(TARGET_SYS)=%.o),$(filter %.o,$(^))) $(LDFLAGS) $(LLIBS) $(LDLIBS) -o $@

# Update libary with new object files.
$(_LIB): $(ROOTDIR)/Makefile Include.make $(_LIB_OBJS) 
	$(AR) rcv $@ $(filter %.o,$(?))
	if [ "$(RANLIB)" ]; then $(RANLIB) $@; fi;

# Update shared libary with new object files.
$(_SHLIB): $(ROOTDIR)/Makefile Include.make $(_LIB_OBJS) 
	$(LD) -shared $(LDFLAGS) $(LLIBS) $(LDLIBS) -o $(SHLIB:%=$(BUILDDIR)/%.so) $(filter %.o,$(?))

# Force creation of $(BUILDDIR) if it doesn't exist.
$(BUILDDIR): 
	@mkdir -p $@

# Rules to create object files in $(BUILDDIR) for C/C++
$(BUILDDIR)/%.o:%.cpp $(BUILDDIR)/%.d
	$(COMPILE.cpp) $(OUTPUT_OPTION) $<
$(BUILDDIR)/%.o: %.c $(BUILDDIR)/%.d
	$(COMPILE.c) $(OUTPUT_OPTION) $<

# Force creation of .d files
$(DEPFILES):

FORCE:

docs:
	cd ${ROOTDIR}/doc; \
	echo "Delete previous files - if they exist"; \
	/bin/rm -rf html/*; \
	echo "Start processing...  *** THIS MAY TAKE AWHILE! ***"; \
	/usr/bin/doxygen  Doxyfile . ;

# TODO: Fix multiple definition of main when have multiple executibles
lint: $(filter %.c,$(SRCS)) $(filter %.c,$(LIB_SRCS)) $(EXES:%=%.c)
	$(LINT) $(LINTFLAGS) $(CPPFLAGS) $(EXES:%=%.c) \
	$(filter %.c,$(SRCS)) $(filter %.c,$(LIB_SRCS)) >linterrs 2>&1

# TODO: Fix multiple definition of main when have multiple executibles
tags:  $(filter %.c,$(SRCS)) $(filter %.c,$(LIB_SRCS)) $(EXES:%=%.c)
	ctags -x $(filter %.c,$(SRCS)) $(filter %.c,$(LIB_SRCS)) \
	$(EXES:%=%.c) > tags

# TODO: Fix multiple definition of main when have multiple executibles
etags:  $(filter %.c,$(SRCS)) $(filter %.c,$(LIB_SRCS)) $(EXES:%=%.c)
	etags -I -o TAGS $(filter %.c,$(SRCS)) $(filter %.c,$(LIB_SRCS)) \
	$(EXES:%=%.c)

# Remove object files left after a make.
clean: FORCE
	@(D="$(SUBDIRS)";				\
	for i in $$D; do ( 				\
	    if [ -d $$i ]; then cd $$i;                                 \
		if [ -e ./build.sh ]; then ./build.sh $@ $$i;           \
		else $(MAKE) $@; fi;                                    \
	    else echo "Warning: No such directory: $$i"; break; fi);    \
	done)
	$(RM) $(BUILDDIR)/*.o core 

# Remove everything created from make process
xclean: FORCE
	@(D="$(SUBDIRS)";				\
	for i in $$D; do ( 				\
	    if [ -d $$i ]; then cd $$i;                                 \
		if [ -e ./build.sh ]; then ./build.sh $@ $$i;           \
		else $(MAKE) $@; fi;                                    \
	    else echo "Warning: No such directory: $$i"; break; fi);    \
	done)
	$(RM) $(BUILDDIR)/*.o $(BUILDDIR)/*.d core *.ln linterrs $(EXES:%=$(BUILDDIR)/%) $(LIB:%=$(BUILDDIR)/lib%.a) $(EXES:%=../bin/%) $(LIB:%=../lib/lib%.a) $(EXES:%=$(BINDIR)/%) $(LIB:%=$(LIBDIR)/lib%.a) 

debug:  FORCE
	@$(MAKE) ROOTDIR=$(ROOTDIR) CFLAGS="$(CFLAGS) -g" LDFLAGS="$(LDFLAGS) -g"

gprof: FORCE
	@$(MAKE) ROOTDIR=$(ROOTDIR) CFLAGS="$(CFLAGS) -pg" LDFLAGS="$(LDFLAGS) -g"

include $(wildcard $(DEPFILES))
