docker rm -f cnn-serving
docker rm -f stable-diffusion
docker rm -f vits-ljs
docker rm -f yolov3
docker rm -f llama-3.1-8b

docker pull hwnam831/cnn-serving
docker pull hwnam831/stable-diffusion
docker pull hwnam831/vits-ljs
docker pull hwnam831/yolov3
docker pull hwnam831/llama-3.1-8b

bash init_container.sh cnn-serving 
bash init_container.sh stable-diffusion 
bash init_container.sh vits-ljs 
bash init_container.sh yolov3
bash init_container.sh llama-3.1-8b 