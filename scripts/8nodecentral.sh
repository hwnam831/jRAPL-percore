#Usage: clusterexp.sh [policy] [limit] [duration]
TOTALCAP=$(($2*8))
DURATION=$(($3+120))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
mkdir -p results;

ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" stable-diffusion bigcluster_"$1" high 1" 2> /dev/null &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" yolov3 bigcluster_"$1" high 3" 2> /dev/null &
ssh hwnam831@ow6 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" vits-ljs  bigcluster_"$1" high 2" 2> /dev/null &
ssh hwnam831@ow8 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" llama-3.1-8b bigcluster_"$1" high 3" 2> /dev/null &

ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" stable-diffusion bigcluster_"$1" low 1" 2> /dev/null &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" yolov3 bigcluster_"$1" low 3" 2> /dev/null &
ssh hwnam831@ow7 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" vits-ljs  bigcluster_"$1" low 2" 2> /dev/null &
ssh hwnam831@ow9 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" llama-3.1-8b bigcluster_"$1" low 3" 2> /dev/null &

python3 ClusterController.py --policy $1 --limit $TOTALCAP --duration $DURATION > results/bigcluster_centralized_$1_$2.csv;
sleep 120;
