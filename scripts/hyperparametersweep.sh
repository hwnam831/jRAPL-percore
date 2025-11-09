#Usage: clusterexp.sh [limit] [duration] [lr] [period] [alpha]
TOTALCAP=$(($1*16))
DURATION=$(($2+120))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
mkdir -p results;

ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" stable-diffusion hyperparameter1_lr"$3"_period"$4"_alpha"$5"_"$1"W high 1" 2> /dev/null &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" yolov3 hyperparameter1_lr"$3"_period"$4"_"alpha"$5"_$1"W high 1" 2> /dev/null &
ssh hwnam831@ow6 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" vits-ljs  hyperparameter1_lr"$3"_period"$4"_alpha"$5"_"$1"W high 1" 2> /dev/null &
ssh hwnam831@ow8 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" llama-3.1-8b hyperparameter1_lr"$3"_period"$4"_alpha"$5"_"$1"W high 1" 2> /dev/null &

ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" stable-diffusion hyperparameter1_lr"$3"_period"$4"_alpha"$5"_"$1"W low 1" 2> /dev/null &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" yolov3 hyperparameter1_lr"$3"_period"$4"_alpha"$5"_"$1"W low 1" 2> /dev/null &
ssh hwnam831@ow7 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" vits-ljs  hyperparameter1_lr"$3"_period"$4"_alpha"$5"_"$1"W low 1" 2> /dev/null &
ssh hwnam831@ow9 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" llama-3.1-8b hyperparameter1_lr"$3"_period"$4"_alpha"$5"_"$1"W low 1" 2> /dev/null &

ssh hwnam831@ow10 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" stable-diffusion hyperparameter2_lr"$3"_period"$4"_alpha"$5"_"$1"W high 2" 2> /dev/null &
ssh hwnam831@ow12 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" yolov3 hyperparameter2_lr"$3"_period"$4"_"alpha"$5"_$1"W high 2" 2> /dev/null &
ssh hwnam831@ow14 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" vits-ljs  hyperparameter2_lr"$3"_period"$4"_alpha"$5"_"$1"W high 2" 2> /dev/null &
ssh hwnam831@ow16 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" llama-3.1-8b hyperparameter2_lr"$3"_period"$4"_alpha"$5"_"$1"W high 2" 2> /dev/null &

ssh hwnam831@ow11 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" stable-diffusion hyperparameter2_lr"$3"_period"$4"_alpha"$5"_"$1"W low 2" 2> /dev/null &
ssh hwnam831@ow13 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" yolov3 hyperparameter2_lr"$3"_period"$4"_alpha"$5"_"$1"W low 2" 2> /dev/null &
ssh hwnam831@ow15 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" vits-ljs  hyperparameter2_lr"$3"_period"$4"_alpha"$5"_"$1"W low 2" 2> /dev/null &
ssh hwnam831@ow17 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" llama-3.1-8b hyperparameter2_lr"$3"_period"$4"_alpha"$5"_"$1"W low 2" 2> /dev/null &

python3 ClusterController.py --policy ml --limit $TOTALCAP --duration $DURATION --periodms $4 --alpha $5 > results/hyperparameter_centralized_lr"$3"_period"$4"_alpha"$5"_"$1"W.csv;
sleep 180;
