import socket

myPort = 4545
if __name__ == '__main__':
    # Set bind address and port

    # Create a socket for receiving connections
    serverSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    serverSocket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    serverSocket.bind((0.0.0.0, myPort))
    serverSocket.listen(1)