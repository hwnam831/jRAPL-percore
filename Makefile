CFLAGS = -fPIC -g -c
TARGET = *.so *.o *.class cpuscalertest
JAVA_HOME = $(shell readlink -f /usr/bin/javac | sed "s:bin/javac::")
JAVA_INCLUDE = $(JAVA_HOME)/include
JAVA_INCLUDE_LINUX = $(JAVA_INCLUDE)/linux
DEFS = -DNOSMT
 
all: lib_shared_CPUScaler lib_shared_perfChecker EnergyCheckUtils.class PerfCheckUtils.class
install: lib_shared_CPUScaler lib_shared_perfChecker
	sudo mkdir -p /usr/lib/jni
	sudo cp *.so /usr/lib/jni/

lib_shared_perfChecker:
	gcc $(CFLAGS) -I $(JAVA_INCLUDE) -I$(JAVA_INCLUDE_LINUX) $(DEFS) perfCheck_percore.c arch_spec.c -lc -lm 
	gcc -I $(JAVA_INCLUDE) -I $(JAVA_INCLUDE_LINUX) $(DEFS) -shared -Wl,-soname,libperfCheck.so -o libperfCheck.so perfCheck_percore.o arch_spec.o -lpfm -lc -lm

lib_shared_CPUScaler:
	gcc $(CFLAGS) -I $(JAVA_INCLUDE) -I$(JAVA_INCLUDE_LINUX) $(DEFS) CPUScaler.c arch_spec.c msr.c dvfs.c -lc -lm
	gcc -I $(JAVA_INCLUDE) -I $(JAVA_INCLUDE_LINUX) $(DEFS) -shared -Wl,-soname,libCPUScaler.so -o libCPUScaler.so CPUScaler.o arch_spec.o msr.o dvfs.o -lc -lm

test: CPUScaler_test.c
	gcc $(CFLAGS) -I $(JAVA_INCLUDE) -I $(JAVA_INCLUDE_LINUX) $(DEFS) CPUScaler_test.c arch_spec.c msr.c -lc -lm
	gcc -I $(JAVA_INCLUDE) -I $(JAVA_INCLUDE_LINUX) $(DEFS) -o cpuscalertest CPUScaler_test.o arch_spec.o msr.o -lc -lm
	sudo ./cpuscalertest
%.class: %.java
	javac $<
clean:
	rm -f $(TARGET)
