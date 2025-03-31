#Usage: clusterexp.sh [policy] [limit] [duration] [controlperiod]
TOTALCAP=$(($2*4))
DURATION=$(($3+120))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
mkdir -p results;
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" llama-3.1-8b llama-3.1-8b config"$4"sec_"$1"_hierarchical" &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" stable-diffusion stable-diffusion config"$4"sec_"$1"_hierarchical" &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" cnn-serving cnn-serving config"$4"sec_"$1"_hierarchical" &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" vits-ljs vits-ljs config"$4"sec_"$1"_hierarchical" &
python3 ClusterController.py --policy $1 --limit $TOTALCAP --duration $DURATION --periodms $(($4*1000)) > results/config"$4"sec_hierarchical_$1_$2.csv;