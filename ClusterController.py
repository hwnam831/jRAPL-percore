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
    print('You pressed Ctrl+C!', file=sys.stderr)
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
        initialflag = False
        if clientAddress not in clients:
            initialflag = True
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
            print("New client at: " + str(clientAddress) + " Now total " + str(len(clients)), file=sys.stderr)
            lockStatus.release()

        try:
            data_ = clientSocket.recv(1024)
            dataStr = data_.decode('UTF-8')
            #print("Time = " + str(time.time()) + " From " + str(clientAddress) + " got " + dataStr[2:])
            dataStrList = dataStr[2:].split(',')
            #print(dataStrList)
            #Running average update
            lockStatus.acquire()
            if initialflag:
                nodeStatuses[clientAddress]['Consumption'] = float(dataStrList[0])
                nodeStatuses[clientAddress]['BIPS'] = float(dataStrList[1])
                nodeStatuses[clientAddress]['dBIPS/dPower'] = float(dataStrList[2])
            else:
                nodeStatuses[clientAddress]['Consumption'] = \
                    nodeStatuses[clientAddress]['Consumption'] * 0.75 + 0.25*float(dataStrList[0])
                nodeStatuses[clientAddress]['BIPS'] = \
                    nodeStatuses[clientAddress]['BIPS']*0.75 + float(dataStrList[1]) * 0.25
                nodeStatuses[clientAddress]['dBIPS/dPower'] = \
                    nodeStatuses[clientAddress]['dBIPS/dPower']*0.75 + float(dataStrList[2])*0.25
            lockStatus.release()
            
            
            msg = "Limit:" + str(nodeStatuses[clientAddress]['Limit'])+"\n"
            clientSocket.send(msg.encode(encoding="utf-8"))
            clientSocket.close()
        except Exception as e:
            print("Error 1 == "  + str(e), file=sys.stderr)
            pass
    print("server stopped", file=sys.stderr)
    serverSocket.close()

power_max = 200
power_min = 35
grad_max = 10.0
alpha = 0.25

def printcsv():
    csvlines=[]
    for c in clients:
        csvlines += [str(nodeStatuses[c]['Limit']),str(nodeStatuses[c]['Consumption']),
                     str(nodeStatuses[c]['BIPS']),str(nodeStatuses[c]['dBIPS/dPower'])]
    print(','.join(csvlines))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-p", "--policy", type=str, choices=['slurm','ml','sin','const'],
                default='const',help="policy")
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
        sleeptime = max(nextTime - time.time(), 0.0001)
        time.sleep(sleeptime)
        nextTime = time.time() + args.periodms/1000
        if len(clients) < 2:
            continue
        lockStatus.acquire()
        totalbips = 0.0
        totalpower = 0.0
        b2p_grads = {}
        for c in clients:
            totalpower += nodeStatuses[c]['Consumption']
            totalbips += nodeStatuses[c]['BIPS']
        for c in clients:
            b2p_grads[c] = 2*(totalbips/totalpower)*nodeStatuses[c]['dBIPS/dPower'] - (totalbips/totalpower)*(totalbips/totalpower)
        if args.policy == "ml":
            sum_newpl = 0
            grad_sum=0
            
            for c in clients:
                grad_sum += b2p_grads[c]
                
            if grad_sum > grad_max:
                lr = grad_max/grad_sum
            elif grad_sum < -grad_max:
                lr = -grad_max/grad_sum
            else:
                lr = 1

            for c in clients:
                curpl = nodeStatuses[c]['Limit']
                newpl = curpl - alpha*(curpl - nodeStatuses[c]['Consumption']) + lr*b2p_grads[c]
                newpl = max(power_min, newpl)
                newpl = min(power_max, newpl)
                sum_newpl += newpl
                nodeStatuses[c]['Limit'] = newpl
            remainder = 0
            eff_len = len(clients)
            coefs = {}
            if sum_newpl > clusterPowerLimit:
                delta = (sum_newpl - clusterPowerLimit)/len(clients)
                for c in clients:
                    newpl = nodeStatuses[c]['Limit'] - delta
                    if newpl < power_min:
                        remainder += power_min - newpl
                        eff_len = eff_len -1
                        newpl = power_min
                        coefs[c] = 0
                    else:
                        coefs[c] = 1
                    nodeStatuses[c]['Limit'] = newpl
                for c in clients:
                    if eff_len <= 0:
                        break
                    nodeStatuses[c]['Limit'] -= coefs[c]*remainder/eff_len
        elif args.policy == 'slurm':
            pool = 0.0
            beta = len(clients) / (len(clients) - 0.99)
            for c in clients:
                diff = nodeStatuses[c]['Limit']-nodeStatuses[c]['Consumption']
                if diff>0.0:
                    pool += 0.5*diff* beta
                    nodeStatuses[c]['Limit'] = nodeStatuses[c]['Limit'] - 0.5*diff* beta
            for c in clients:
                nodeStatuses[c]['Limit'] = nodeStatuses[c]['Limit'] + pool/len(clients)
        elif args.policy == 'sin': #sin
            counter = counter + 1
            clusterPowerLimit = 3*args.limit/4 + args.limit * math.sin((counter/40) * 2 * math.pi)/4
            #print(clusterPowerLimit)
            for c in clients:
                nodeStatuses[c]['Limit'] = clusterPowerLimit/len(clients)
        else:
            pass
        lockStatus.release()
        printcsv()
        if deadline is not None and time.time() > deadline:
            serverRunning = False
    print("controller stopped", file=sys.stderr)
    clientcount = 0
    headerstr = []
    for c in clients:
        clientcount += 1
        headerstr += ['Limit:' + str(clientcount),'Consumption:' + str(clientcount),
                      'BIPS:' + str(clientcount),'dBIPS/dPower:' + str(clientcount)]
    print(','.join(headerstr))
    controllerserver.join()
    #TODO: test sinusoidal
    # Create a socket for receiving connections
    
