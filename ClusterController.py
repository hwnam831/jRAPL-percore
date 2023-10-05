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
    global serverRunning
    global clusterPowerLimit
    global nodeStatuses
    global lockStatus
    global clients

    clusterPowerLimit = plimit
    serverSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    serverSocket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    serverSocket.bind(('0.0.0.0', myPort))
    serverSocket.listen(1)
    while serverRunning:
        #time.sleep(periodms/1000)
        (clientSocket, address) = serverSocket.accept()
        clientAddress = address[0]
        if clientAddress not in clients:
            lockStatus.acquire()
            clients.append(clientAddress)
            
            nodeStatuses[clientAddress] = {
                'Limit' : clusterPowerLimit/len(clients),
                'Consumption' : clusterPowerLimit/len(clients),
                'BIPS' : 0.0,
                'dBIPS/dPower' : 0.0
            }
            for c in clients:
                nodeStatuses[c]['Limit'] = clusterPowerLimit/len(clients)
            print("New client at: " + str(clientAddress) + " Now total " + str(len(clients)))
            lockStatus.release()

        try:
            data_ = clientSocket.recv(1024)
            dataStr = data_.decode('UTF-8')
            print("Time = " + str(time.time()) + " From " + str(clientAddress) + " got " + dataStr[2:])
            dataStrList = dataStr[2:].split(',')
            print(dataStrList)
            lockStatus.acquire()
            nodeStatuses[clientAddress]['Consumption'] = float(dataStrList[0])
            nodeStatuses[clientAddress]['BIPS'] = float(dataStrList[1])
            nodeStatuses[clientAddress]['dBIPS/dPower'] = float(dataStrList[2])
            lockStatus.release()
            
            
            msg = "Limit:" + str(nodeStatuses[clientAddress]['Limit'])+"\n"
            clientSocket.send(msg.encode(encoding="utf-8"))
            clientSocket.close()
        except Exception as e:
            print("Error 1 == "  + str(e))
            pass
    serverSocket.close()



if __name__ == '__main__':
    # Set bind address and port
    ControllerServer(100,120)
    # Create a socket for receiving connections
    
