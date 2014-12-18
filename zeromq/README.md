## ZeroMQ

This module uses the native ZeroMQ library, but is API compatible with the pure-Java implementation, JeroMQ. The native API supports Unix domain sockets, and greater performance than that of the Java implementation. Unix domain sockets provide a performant mechinism for multiple host applications to comunicate with the on-host agent, without touching the host NIC (virtual or otherwise). 

### Installing native support

1. Install ZeroMQ. 

```
brew install zeromq
```

2. Install pkg-config.

```
brew install pkg-config
```

3. Symlink pkg.m4 into /usr/share/aclocal, which is needed for the jzmq build

```
sudo mkdir /usr/share/aclocal
sudo ln -s /usr/local/share/aclocal/pkg.m4 /usr/share/aclocal/pkg.m4
```

4. Clone the jzmq source code

```
git clone git@github.com:zeromq/jzmq.git
cd jzmq
```

5. Run the build

```
./autogen.sh
./configure
make
make install
```

You’ll end up with the zmq.jar in `/usr/local/share/java/` and the native lib in `/usr/local/lib`





