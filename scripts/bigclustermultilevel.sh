#Usage: clusterexp.sh [limit] [duration]
TOTALCAP=$(($1*32))
DURATION=$(($2+120))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
mkdir -p results;
#LLAMA

cd /mydata/workspace/jrapl;
mkdir -p results;
#LLAMA
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" stable-diffusion bigcluster1_multilevel high 1" 2> /dev/null &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" cnn-serving bigcluster1_multilevel high 1" 2> /dev/null &
ssh hwnam831@ow6 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" vits-ljs  bigcluster1_multilevel high 1" 2> /dev/null &
ssh hwnam831@ow8 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" llama-3.1-8b bigcluster1_multilevel high 1" 2> /dev/null &

ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" stable-diffusion bigcluster1_multilevel low 1" 2> /dev/null &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" cnn-serving bigcluster1_multilevel low 1" 2> /dev/null &
ssh hwnam831@ow7 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" vits-ljs  bigcluster1_multilevel low 1" 2> /dev/null &
ssh hwnam831@ow9 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" llama-3.1-8b bigcluster1_multilevel low 1" 2> /dev/null &

ssh hwnam831@ow10 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" stable-diffusion bigcluster2_multilevel high 2" 2> /dev/null &
ssh hwnam831@ow12 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" cnn-serving bigcluster2_multilevel high 2" 2> /dev/null &
ssh hwnam831@ow14 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" vits-ljs  bigcluster2_multilevel high 2" 2> /dev/null &
ssh hwnam831@ow16 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" llama-3.1-8b bigcluster2_multilevel high 2" 2> /dev/null &

ssh hwnam831@ow11 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" stable-diffusion bigcluster2_multilevel low 2" 2> /dev/null &
ssh hwnam831@ow13 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" cnn-serving bigcluster2_multilevel low 2" 2> /dev/null &
ssh hwnam831@ow15 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" vits-ljs  bigcluster2_multilevel low 2" 2> /dev/null &
ssh hwnam831@ow17 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" llama-3.1-8b bigcluster2_multilevel low 2" 2> /dev/null &

ssh hwnam831@ow18 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" stable-diffusion bigcluster3_multilevel high 3" 2> /dev/null &
ssh hwnam831@ow20 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" cnn-serving bigcluster3_multilevel high 3" 2> /dev/null &
ssh hwnam831@ow22 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" vits-ljs  bigcluster3_multilevel high 3" 2> /dev/null &
ssh hwnam831@ow24 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" llama-3.1-8b bigcluster3_multilevel high 3" 2> /dev/null &

ssh hwnam831@ow19 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" stable-diffusion bigcluster3_multilevel low 3" 2> /dev/null &
ssh hwnam831@ow21 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" cnn-serving bigcluster3_multilevel low 3" 2> /dev/null &
ssh hwnam831@ow23 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" vits-ljs  bigcluster3_multilevel low 3" 2> /dev/null &
ssh hwnam831@ow25 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" llama-3.1-8b bigcluster3_multilevel low 3" 2> /dev/null &


ssh hwnam831@ow26 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" stable-diffusion bigcluster4_multilevel high 4" 2> /dev/null &
ssh hwnam831@ow28 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" cnn-serving bigcluster4_multilevel high 4" 2> /dev/null &
ssh hwnam831@ow30 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" vits-ljs  bigcluster4_multilevel high 4" 2> /dev/null &
ssh hwnam831@ow32 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" llama-3.1-8b bigcluster4_multilevel high 4" 2> /dev/null &

ssh hwnam831@ow27 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" stable-diffusion bigcluster4_multilevel low 4" 2> /dev/null &
ssh hwnam831@ow29 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" cnn-serving bigcluster4_multilevel low 4" 2> /dev/null &
ssh hwnam831@ow31 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" vits-ljs  bigcluster4_multilevel low 4" 2> /dev/null &
ssh hwnam831@ow33 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$2" "$1" llama-3.1-8b bigcluster4_multilevel low 4" 2> /dev/null &
python3 MultilevelController.py --limit $TOTALCAP --duration $DURATION > results/bigcluster_multilevel_$1.csv;
