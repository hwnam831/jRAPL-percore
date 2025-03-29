#Usage: clusterexp.sh [policy] [limit] [duration]
TOTALCAP=$(($2*4))
DURATION=$(($3+120))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
mkdir -p results;
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$2" llama-3.1-8b stable-diffusion config2_"$1"_central" 2> /dev/null &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$2" stable-diffusion cnn-serving config2_"$1"_central" 2> /dev/null &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$2" cnn-serving vits-ljs config2_"$1"_central" 2> /dev/null &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$2" vits-ljs llama-3.1-8b config2_"$1"_central" 2> /dev/null &
python3 ClusterController.py --policy $1 --limit $TOTALCAP --duration $DURATION > results/config2_centralized_$1_$2.csv;