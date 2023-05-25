#twocpuexp.sh [policy] [app1] [app2] [cap]
ssh hwnam831@ow1 "bash /local/repository/resetow.sh; sleep 150; bash /local/repository/install_functions.sh";
ssh hwnam831@ow1 "cd /mydata/workspace/faas-profiler; ./WorkloadInvoker -c "$2"_"$3".json" &
sudo java -cp $PWD":"$PWD"/argparse4j-0.9.0.jar" LocalController --policy $1 --duration 90 --cap $4 > $1_$2_$3_$4.csv