#Usage: clusterexp.sh [policy] [limit] [duration]
TOTALCAP=$(($2*4))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
mkdir -p results;
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" llama-3.1-8b cnn-serving" &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" stable-diffusion vits-ljs " &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" cnn-serving stable-diffusion" &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" vits-ljs llama-3.1-8b " &
python3 ClusterController.py --policy $1 --limit $TOTALCAP --duration $3 > results/centralized_$1_$2.csv;