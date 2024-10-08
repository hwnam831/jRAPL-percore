CFLAGS = -fPIC -g -c
TARGET = *.so *.o *.class cpuscalertest
JAVA_HOME = $(shell readlink -f /usr/bin/javac | sed "s:bin/javac::")
JAVA_INCLUDE = $(JAVA_HOME)/include
JAVA_INCLUDE_LINUX = $(JAVA_INCLUDE)/linux
CLASSPATH = $(PWD):$(PWD)/argparse4j-0.9.0.jar
DEFS = -DNOSMT
TORCHDIR=/mydata/workspace/libtorch
MYDEPS=-Wl,-rpath,$(TORCHDIR)/lib $(TORCHDIR)/lib/libtorch.so $(TORCHDIR)/lib/libc10.so $(TORCHDIR)/lib/libkineto.a -Wl,--no-as-needed,"$(TORCHDIR)/lib/libtorch_cpu.so" -Wl,--as-needed $(TORCHDIR)/lib/libc10.so -lpthread -Wl,--no-as-needed,"$(TORCHDIR)/lib/libtorch.so" -Wl,--as-needed
CXX_DEFINES = -DUSE_C10D_GLOO -DUSE_DISTRIBUTED -DUSE_RPC -DUSE_TENSORPIPE
CXX_INCLUDES = -isystem $(TORCHDIR)/include -isystem $(TORCHDIR)/include/torch/csrc/api/include
CXX_FLAGS = -D_GLIBCXX_USE_CXX11_ABI=0 -fPIC -D_GLIBCXX_USE_CXX11_ABI=0 -std=gnu++17

all: install microbench classes

classes: EnergyCheckUtils.class PerfCheckUtils.class TraceCollector.class PerfCounters.class MLModel.class LocalController.class

matmul:
	gcc -O3 -o matmul matrix-mul-pthread.c -lpthread
microbench: microbench.c
	gcc -O3 -o microbench microbench.c
install: libCPUScaler.so libperfCheck.so libjtorch.so libMLModel.so
	sudo mkdir -p /usr/lib/jni
	sudo cp *.so /usr/lib/jni/

libperfCheck.so: perfCheck_percore.c arch_spec.c
	gcc $(CFLAGS) -I $(JAVA_INCLUDE) -I$(JAVA_INCLUDE_LINUX) $(DEFS) perfCheck_percore.c arch_spec.c -lc -lm 
	gcc -I $(JAVA_INCLUDE) -I $(JAVA_INCLUDE_LINUX) $(DEFS) -shared -Wl,-soname,libperfCheck.so -o libperfCheck.so perfCheck_percore.o arch_spec.o -lpfm -lc -lm

libCPUScaler.so: CPUScaler.c arch_spec.c msr.c dvfs.c
	gcc $(CFLAGS) -I $(JAVA_INCLUDE) -I$(JAVA_INCLUDE_LINUX) $(DEFS) CPUScaler.c arch_spec.c msr.c dvfs.c -lc -lm
	gcc -I $(JAVA_INCLUDE) -I $(JAVA_INCLUDE_LINUX) $(DEFS) -shared -Wl,-soname,libCPUScaler.so -o libCPUScaler.so CPUScaler.o arch_spec.o msr.o dvfs.o -lc -lm

libjtorch.so: jtorch.cc
	/usr/bin/c++ $(CXX_DEFINES) -I $(JAVA_INCLUDE) -I$(JAVA_INCLUDE_LINUX) $(CXX_INCLUDES) $(CXX_FLAGS) -o jtorch.cc.o -c jtorch.cc
	/usr/bin/c++ -fPIC  -D_GLIBCXX_USE_CXX11_ABI=0  -shared -Wl,-soname,libjtorch.so -o libjtorch.so jtorch.cc.o $(MYDEPS)

libMLModel.so: endmodel.cc
	/usr/bin/c++ $(CXX_DEFINES) -I $(JAVA_INCLUDE) -I$(JAVA_INCLUDE_LINUX) $(CXX_INCLUDES) $(CXX_FLAGS) -o endmodel.cc.o -c endmodel.cc
	/usr/bin/c++ -fPIC  -D_GLIBCXX_USE_CXX11_ABI=0  -shared -Wl,-soname,libMLModel.so -o libMLModel.so endmodel.cc.o $(MYDEPS)

test: CPUScaler_test.c
	gcc $(CFLAGS) -I $(JAVA_INCLUDE) -I $(JAVA_INCLUDE_LINUX) $(DEFS) CPUScaler_test.c arch_spec.c msr.c -lc -lm
	gcc -I $(JAVA_INCLUDE) -I $(JAVA_INCLUDE_LINUX) $(DEFS) -o cpuscalertest CPUScaler_test.o arch_spec.o msr.o -lc -lm
	sudo ./cpuscalertest
%.class: %.java
	javac -g -cp $(CLASSPATH) $<
clean:
	rm -f $(TARGET)
