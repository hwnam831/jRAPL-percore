import math
import socket
import time
import threading
import argparse
import signal
import sys


myPort = 4545
serverRunning = True
lockStatus = threading.Lock()
clients = []
powerTargets = {}
nodeStatuses = {}
clusterPowerLimit = 120.0

def signal_handler(sig, frame):
    print('You pressed Ctrl+C!')
    global serverRunning
    serverRunning = False

def ControllerServer():

    global nodeStatuses
    global clients

    #clusterPowerLimit = plimit
    serverSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    serverSocket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    serverSocket.bind(('0.0.0.0', myPort))
    serverSocket.listen(1)
    serverSocket.settimeout(1.0)
    while serverRunning:
        #time.sleep(periodms/1000)
        try:
            (clientSocket, address) = serverSocket.accept()
        except:
            #print("Server timeout. Continue")
            continue
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
    print("server stopped")
    serverSocket.close()



if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-p", "--policy", type=str, choices=['slurm','ml','sin'],
                default='sin',help="policy")
    parser.add_argument("-l", "--limit", type=float,
                default='160',help="cluster power limit")
    parser.add_argument("--periodms", type=float,
                default='400',help="time period in milliseconds")
    parser.add_argument("--duration", type=float,
                default='-1',help="experiment duration in seconds")
    args=parser.parse_args()
    signal.signal(signal.SIGINT, signal_handler)
    # Set bind address and port

    clusterPowerLimit = args.limit
    controllerserver = threading.Thread(target=ControllerServer)
    controllerserver.start()

    nextTime = time.time() + args.periodms/1000
    counter = 0.0
    deadline = None
    if args.duration > 0:
        deadline = time.time() + args.duration
    while serverRunning:
        counter = counter + 1
        lockStatus.acquire()

        if args.policy == "ml":
            pass
        elif args.policy == 'slurm':
            pass
        else: #sin
            clusterPowerLimit = 3*args.limit/4 + args.limit * math.sin((counter/40) * 2 * math.pi)/4
            print(clusterPowerLimit)
            for c in clients:
                nodeStatuses[c]['Limit'] = clusterPowerLimit/len(clients)
        lockStatus.release()
        sleeptime = max(nextTime - time.time(), 0.0001)

        time.sleep(sleeptime)
        nextTime = time.time() + args.periodms/1000
        if deadline is not None and time.time() > deadline:
            serverRunning = False
    print("controller stopped")
    controllerserver.join()
    #TODO: test sinusoidal
    # Create a socket for receiving connections
    
