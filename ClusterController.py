import requests
import json
import os
import socket
import time
import threading
import sys
import signal
import subprocess
import re
import random

myPort = 4545
serverRunning = True
lockStatus = threading.Lock()
clients = []
powerTargets = {}
nodeStatuses = {}
clusterPowerLimit = 120.0

def ControllerServer(periodms, plimit):
    clusterPowerLimit = plimit
    serverSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    serverSocket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    serverSocket.bind(('0.0.0.0', myPort))
    serverSocket.listen(1)
    while True:
        time.sleep(periodms/1000)
        (clientSocket, address) = serverSocket.accept()
        clientAddress = address[0]
        if clientAddress not in clients:
            clients.append(clientAddress)
            for c in clients:
                powerTargets[c] = clusterPowerLimit/len(clients)
            nodeStatuses[clientAddress] = {
                'Limit' : clusterPowerLimit/len(clients),
                'Consumption' : clusterPowerLimit/len(clients),
                'BIPS' : 0.0,
                'dBIPS/dPower' : 0.0
            }
            print("New client at: " + str(clientAddress) + ". Now total " + str(len(clients)))
        try:
            data_ = clientSocket.recv(1024)
            dataStr = data_.decode('UTF-8')
            dataStrList = dataStr.split(',')
            message = json.loads(dataStrList[-1])
        except Exception as e:
            print("Error 1 == " + str(dataStr) + " == " + str(e))
            pass

        lockStatus.acquire()
        for func in list(mapFuncToSetCores):
            container = mapFuncToContainers[func]
            output = ((subprocess.check_output("docker ps -q -f id="+str(container), shell=True)).decode("utf-8"))[:-1]
            
            # The container has been deleted
            if output == "":
                delete_func(func)

            # The container is still running
            # To check if the container is safe to donate cores
            '''
            else:
                try:
                    configs = {"Q":"Safe?"}
                    cmdQA = "docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' " + str(container)
                    output = ((subprocess.check_output(cmdQA, shell=True)).decode("utf-8"))[:-1]
                    while True:
                        try:
                            rsp = requests.post('http://' + str(output) + ':1111', json=configs)
                            break
                        except:
                            pass
                    isSafe = (json.loads(rsp.text))["Response"]
                    mapFuncToRegions[func] = isSafe
                except:
                    pass
                '''
                
        lockStatus.release()



threadChecker = threading.Thread(target=checkThread)
threadChecker.start()

if __name__ == '__main__':
    # Set bind address and port

    # Create a socket for receiving connections
    
