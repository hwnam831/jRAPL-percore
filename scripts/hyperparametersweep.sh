#Usage: clusterexp.sh [limit] [duration] [lr] [period] [alpha]
TOTALCAP=$(($1*8))
DURATION=$(($2))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
mkdir -p results;

ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" stable-diffusion hyperparameter_lr"$3"_period"$4"_alpha"$5"_"$1"W high 1" 2> /dev/null &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" cnn-serving hyperparameter_lr"$3"_period"$4"_"alpha"$5"_$1"W high 3" 2> /dev/null &
ssh hwnam831@ow6 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" vits-ljs  hyperparameter_lr"$3"_period"$4"_alpha"$5"_"$1"W high 2" 2> /dev/null &
ssh hwnam831@ow8 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" llama-3.1-8b hyperparameter_lr"$3"_period"$4"_alpha"$5"_"$1"W high 3" 2> /dev/null &

ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" stable-diffusion hyperparameter_lr"$3"_period"$4"_alpha"$5"_"$1"W low 1" 2> /dev/null &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" cnn-serving hyperparameter_lr"$3"_period"$4"_alpha"$5"_"$1"W low 3" 2> /dev/null &
ssh hwnam831@ow7 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" vits-ljs  hyperparameter_lr"$3"_period"$4"_alpha"$5"_"$1"W low 2" 2> /dev/null &
ssh hwnam831@ow9 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$DURATION" "$1" llama-3.1-8b hyperparameter_lr"$3"_period"$4"_alpha"$5"_"$1"W low 3" 2> /dev/null &

python3 ClusterController.py --policy ml --limit $TOTALCAP --duration $DURATION --periodms $4 --alpha $5 > results/hyperparameter_centralized_lr"$3"_period"$4"_alpha"$5"_"$1"W.csv;
sleep 120;
