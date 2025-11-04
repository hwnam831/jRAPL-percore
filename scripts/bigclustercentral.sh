#Usage: clusterexp.sh [policy] [limit] [duration]
TOTALCAP=$(($2*16))
DURATION=$(($3+120))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
mkdir -p results;
#stable-diffusion
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" stable-diffusion bigcluster1_"$1" high 1" 2> /dev/null &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" stable-diffusion bigcluster1_"$1" low 1" 2> /dev/null &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" stable-diffusion  bigcluster2_"$1" high 2" 2> /dev/null &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" stable-diffusion bigcluster2_"$1" low 2" 2> /dev/null &

#yolov3
ssh hwnam831@ow6 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" yolov3 bigcluster1_"$1" high 1" 2> /dev/null &
ssh hwnam831@ow7 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" yolov3 bigcluster1_"$1" low 1" 2> /dev/null &
ssh hwnam831@ow8 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" yolov3  bigcluster2_"$1" high 2" 2> /dev/null &
ssh hwnam831@ow9 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" yolov3 bigcluster2_"$1" low 2" 2> /dev/null &

#vits-ljs
ssh hwnam831@ow10 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" vits-ljs bigcluster1_"$1" high 1" 2> /dev/null &
ssh hwnam831@ow11 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" vits-ljs bigcluster1_"$1" low 1" 2> /dev/null &
ssh hwnam831@ow12 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" vits-ljs  bigcluster2_"$1" high 2" 2> /dev/null &
ssh hwnam831@ow13 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" vits-ljs bigcluster2_"$1" low 2" 2> /dev/null &

#llama-3.1-8b
ssh hwnam831@ow14 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" llama-3.1-8b bigcluster1_"$1" high 1" 2> /dev/null &
ssh hwnam831@ow15 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" llama-3.1-8b bigcluster1_"$1" low 1" 2> /dev/null &
ssh hwnam831@ow16 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" llama-3.1-8b  bigcluster2_"$1" high 2" 2> /dev/null &
ssh hwnam831@ow17 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" llama-3.1-8b bigcluster2_"$1" low 2" 2> /dev/null &

python3 ClusterController.py --policy $1 --limit $TOTALCAP --duration $DURATION > results/bigcluster_centralized_$1_$2.csv;
sleep 180;