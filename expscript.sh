sudo java TraceCollector 180 > $1_$2.csv & ssh hwnam831@ow1 "cd /mydata/workspace/faas-profiler; ./WorkloadInvoker -c "$1".json"