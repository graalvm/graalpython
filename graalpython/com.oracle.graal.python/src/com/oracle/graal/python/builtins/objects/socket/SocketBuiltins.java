/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.socket;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSocket)
@SuppressWarnings("unused")
public class SocketBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SocketBuiltinsFactory.getFactories();
    }

    // accept()
    @Builtin(name = "_accept", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AcceptNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object accept(PSocket socket) {
            try {
                SocketChannel acceptSocket = socket.getServerSocket().accept();
                if (acceptSocket == null){
                    throw raise(PythonBuiltinClassType.OSError);
                }
                SocketAddress addr = acceptSocket.getLocalAddress();
                if(!acceptSocket.socket().isBound() || addr == null) {
                    throw raise(PythonBuiltinClassType.OSError);
                }
                PSocket newSocket = factory().createSocket(socket.getFamily(),socket.getType(),socket.getProto());
                int fd = getContext().getResources().openSocket(newSocket);
                newSocket.setFileno(fd);
                newSocket.setSocket(acceptSocket);
                Object[] output = {fd, ((InetSocketAddress) addr).getAddress().getHostAddress()};
                return factory().createTuple(output);
            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }
    }

    // bind(address)
    @Builtin(name = "bind", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class BindNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object bind(PSocket socket, PTuple address) {
            Object[] hostAndPort = address.getArray();

            int port = (int)hostAndPort[1];

            if (port >= 65536 || port < 0) {
                throw raise(PythonBuiltinClassType.OverflowError);
            }

            socket.serverHost = (String) hostAndPort[0];
            socket.serverPort = port;
            return PNone.NONE;
        }
    }

    // close()
    @Builtin(name = "close", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object close(VirtualFrame frame, PSocket socket) {
            if (socket.getSocket() != null) {
                if (!socket.getSocket().isOpen()) {
                    throw raiseOSError(frame, OSErrorEnum.EBADF, "Bad file descriptor");
                }

                try {
                    socket.getSocket().close();
                } catch (IOException e) {
                    throw raiseOSError(frame, OSErrorEnum.ENOTSOCK, "Bad file descriptor");
                }
            }
            else if (socket.getServerSocket() != null) {
                if (!socket.getServerSocket().isOpen()) {
                    throw raiseOSError(frame, OSErrorEnum.ENOTSOCK, "Bad file descriptor");
                }

                try {
                    socket.getServerSocket().close();
                } catch (IOException e) {
                    throw raiseOSError(frame, OSErrorEnum.ENOTSOCK, "Bad file descriptor");
                }
            }
            getContext().getResources().closeSocket(socket.getFileno());
            return PNone.NONE;
        }
    }

    // connect(address)
    @Builtin(name = "connect", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ConnectNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object connect(PSocket socket, PTuple address) {
            Object[] hostAndPort = address.getArray();
            try {
                InetSocketAddress socketAddress = new InetSocketAddress((String) hostAndPort[0], (Integer) hostAndPort[1]);
                SocketChannel channel = SocketChannel.open();
                channel.connect(socketAddress);
                return PNone.NONE;
            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }
    }

    // getpeername()
    @Builtin(name = "getpeername", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetPeerNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object get(PSocket socket) {
            if (socket.getSocket() == null){
                throw raise(PythonBuiltinClassType.OSError, "[Errno 57] Socket is not connected");
            }

            try {
                InetSocketAddress addr = (InetSocketAddress) socket.getSocket().getRemoteAddress();
                return factory().createTuple(new Object[]{addr.getAddress().getHostAddress(), addr.getPort()});
            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }
    }

    // getsockname()
    @Builtin(name = "getsockname", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetSockNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object get(PSocket socket) {
            if (socket.getServerSocket() != null) {
                try {
                    InetSocketAddress addr = (InetSocketAddress) socket.getServerSocket().getLocalAddress();
                    return factory().createTuple(new Object[]{addr.getAddress().getHostAddress(), addr.getPort()});
                } catch (IOException e) {
                    throw raise(PythonBuiltinClassType.OSError);
                }
            }

            if (socket.getSocket() != null){
                try {
                    InetSocketAddress addr = (InetSocketAddress) socket.getSocket().getLocalAddress();
                    return factory().createTuple(new Object[]{addr.getAddress().getHostAddress(), addr.getPort()});
                } catch (IOException e) {
                    throw raise(PythonBuiltinClassType.OSError);
                }
            }

            if (socket.serverHost != null) {
                return factory().createTuple(new Object[]{socket.serverHost, socket.serverPort});
            }

            return factory().createTuple(new Object[]{"0.0.0.0", 0});
        }
    }

    // getblocking()
    @Builtin(name = "getblocking", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetBlockingNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean get(PSocket socket) {
            return socket.isBlocking();
        }
    }

    // gettimeout
    @Builtin(name = "gettimeout", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetTimeoutNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(PSocket socket) {
            return PNone.NONE;
        }
    }

    // listen
    @Builtin(name = "listen", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ListenNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object listen(PSocket socket, int backlog) {
            try {
                InetAddress host = InetAddress.getByName(socket.serverHost);
                InetSocketAddress socketAddress = new InetSocketAddress(host, socket.serverPort);

                ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                // calling bind with port 0 will take the first available
                // for some reason this only works on the ServerSocket not on the ServerSocketChannel
                serverSocketChannel.socket().bind(socketAddress, backlog);
                serverSocketChannel.configureBlocking(socket.isBlocking());

                socket.setServerSocket(serverSocketChannel);
                return PNone.NONE;
            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }

        @Specialization
        @TruffleBoundary
        Object listen(PSocket socket, PNone backlog) {
            return listen(socket, 50);
        }
    }

    // recv(bufsize[, flags])
    @Builtin(name = "recv", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class RecvNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object recv(PSocket socket, int bufsize, int flags) {
            return recv(socket,bufsize,PNone.NONE);
        }
        @Specialization
        @TruffleBoundary
        PBytes recv(PSocket socket, int bufsize, PNone flags) {
            SocketChannel nativeSocket = socket.getSocket();
            ByteBuffer readBytes = ByteBuffer.allocate(bufsize);
            try {
                int length = nativeSocket.read(readBytes);
                return factory().createBytes(Arrays.copyOfRange(readBytes.array(), 0, length));
            } catch (IOException | NullPointerException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }
    }

    // recvfrom(bufsize[, flags])
    @Builtin(name = "recvfrom", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class RecvFromNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object recvFrom(PSocket socket, int bufsize, int flags) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
        @Specialization
        Object recvFrom(PSocket socket, int bufsize, PNone flags) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    // recv_into(bufsize[, flags])
    @Builtin(name = "recv_into", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class RecvIntoNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object recvInto(PSocket socket, PByteArray buffer) {
            byte[] targetBuffer = new byte[buffer.getSequenceStorage().length()];

            int length = fillBuffer(socket, targetBuffer);
            // TODO: seems dirty, is there a better way to fill a byte array?

            for (int i = 0; i < targetBuffer.length; i++) {
                buffer.getSequenceStorage().insertItem(i, targetBuffer[i]);
            }

            return length;
        }

        int fillBuffer(PSocket socket, byte[] buffer) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            SocketChannel nativeSocket = socket.getSocket();
            try {
                return nativeSocket.read(byteBuffer);
            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }
    }

    // recvmsg(bufsize[, ancbufsize[, flags]])
    @Builtin(name = "recvmsg", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class RecvMsgNode extends PythonBuiltinNode {
        @Specialization
        Object recvFrom(PSocket socket, int bufsize, int ancbufsize, int flags) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
        @Specialization
        Object recvFrom(PSocket socket, int bufsize, int ancbufsize, PNone flags) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
        @Specialization
        Object recvFrom(PSocket socket, int bufsize, PNone ancbufsize, PNone flags) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    // send(bytes[, flags])
    @Builtin(name = "send", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SendNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object send(PSocket socket, PBytes bytes, int flags) {
            return send(socket,bytes,PNone.NONE);
        }

        @Specialization
        @TruffleBoundary
        Object send(PSocket socket, PBytes bytes, PNone flags) {
            if (socket.getSocket() == null) {
                throw raise(PythonBuiltinClassType.OSError);
            }

            if (!socket.getSocket().isOpen()) {
                throw raise(PythonBuiltinClassType.OSError);
            }

            try {
                ByteSequenceStorage byteSequenceStorage = (ByteSequenceStorage) bytes.getSequenceStorage();
                ByteBuffer buffer = ByteBuffer.wrap(byteSequenceStorage.getInternalByteArray());
                socket.getSocket().write(buffer);

                return PNone.NONE;
            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }
    }

    // sendall(bytes[, flags])
    @Builtin(name = "sendall", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SendAllNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object sendAll(PSocket socket, PIBytesLike bytes, int flags) {
            return sendAll(socket, bytes, PNone.NONE);
        }

        @Specialization(guards = {"isNoValue(flags)"})
        @TruffleBoundary
        Object sendAll(PSocket socket, PIBytesLike bytes, @SuppressWarnings("unused") PNone flags) {
            try {
                ByteSequenceStorage byteSequenceStorage = (ByteSequenceStorage) bytes.getSequenceStorage();
                ByteBuffer buffer = ByteBuffer.wrap(byteSequenceStorage.getInternalByteArray());
                socket.getSocket().write(buffer);

                return PNone.NONE;
            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }
    }

    // sendto(bytes, address)
    // sendto(bytes, flags, address)
    @Builtin(name = "sendto", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class SendToNode extends PythonBuiltinNode {
        @Specialization
        Object sendTo(PSocket socket, Object bytes, int flags, Object address) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
        @Specialization
        Object sendTo(PSocket socket, Object bytes, PNone flags, Object address) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    // sendmsg(buffers[, ancdata[, flags[, address]]])
    @Builtin(name = "sendmsg", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 5)
    @GenerateNodeFactory
    abstract static class SendMsgNode extends PythonBuiltinNode {
        @Specialization
        Object sendMsg(PSocket socket, Object buffers, Object ancdata, int flags, Object address) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "setblocking", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetBlockingNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object setBlocking(PSocket socket, boolean blocking) {
            socket.setBlocking(blocking);

            try {
                if (socket.getSocket() != null) {
                    socket.getSocket().configureBlocking(socket.isBlocking());
                }

                if (socket.getServerSocket() != null) {
                    socket.getServerSocket().configureBlocking(socket.isBlocking());
                }
            } catch (IOException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }

            return PNone.NONE;
        }
    }

    // settimeout(value)
    @Builtin(name = "settimeout", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetTimeoutNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object setTimeout(PSocket socket, Integer value) {
            try {
                if (socket.getSocket() != null) {
                    socket.getSocket().socket().setSoTimeout(value);
                }

                if (socket.getServerSocket() != null) {
                    socket.getServerSocket().socket().setSoTimeout(value);
                }
            } catch (SocketException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }

            return PNone.NONE;
        }
        @Specialization
        Object setTimeout(PSocket socket, double value) {
            Integer intValue = (int) value;
            return setTimeout(socket, intValue);
        }
    }

    // shutdown(how)
    @Builtin(name = "shutdown", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class shutdownNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object family(PSocket socket, int how) {
            if (socket.getSocket() != null) {
                try {
                    if (how == 0 || how == 2) {
                        socket.getSocket().shutdownInput();
                    }
                    if (how == 1 || how == 2) {
                        socket.getSocket().shutdownOutput();
                    }
                }
                catch (IOException e) {
                    throw raise(PythonBuiltinClassType.OSError);
                }
            }
            else {
                throw raise(PythonBuiltinClassType.OSError);
            }
            return PNone.NO_VALUE;
        }
    }

    // family
    @Builtin(name = "family", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SocketFamilyNode extends PythonUnaryBuiltinNode {
        @Specialization
        int family(PSocket socket) {
            return socket.getFamily();
        }
    }

    // type
    @Builtin(name = "type", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SocketTypeNode extends PythonUnaryBuiltinNode {
        @Specialization
        int type(PSocket socket) {
            return socket.getType();
        }
    }

    // proto
    @Builtin(name = "proto", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SockProtoNode extends PythonUnaryBuiltinNode {
        @Specialization
        int proto(PSocket socket) {
            return socket.getProto();
        }
    }

    // fileno
    @Builtin(name = "fileno", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SockFilenoNode extends PythonUnaryBuiltinNode {
        @Specialization
        int fileno(PSocket socket) {
            return socket.getFileno();
        }
    }

    // detach
    @Builtin(name = "detach", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SockDetachNode extends PythonUnaryBuiltinNode {
        @Specialization
        int detach(PSocket socket) {
            return socket.getFileno();
        }
    }

    @Builtin(name = "_setsockopt", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class SetSockOptNode extends PythonBuiltinNode {
        @Specialization
        Object setSockOpt(PSocket socket, Object level, Object optname, Object value, Object optlen) {
            return PNone.NONE;
        }
    }

}
