import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class NioTelnetServer {
    private static final String LS_COMMAND = "\tls     view all files from current directory";
    private static final String MKDIR_COMMAND = "\tmkdir  view all files from current directory";

    Path pathServer = Path.of("server");     //Переменная с корневым путем.

    private final ByteBuffer buffer = ByteBuffer.allocate(512);

    private Map<SocketChannel, String> clients = new HashMap<SocketChannel, String>();

    public NioTelnetServer() throws Exception {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(5679));
        server.configureBlocking(false);
        Selector selector = Selector.open();

        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server started");
        while (server.isOpen()) {
            selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                } else if (key.isReadable()) {
                    handleRead(key, selector);
                }
                iterator.remove();
            }
        }
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        System.out.println("Client connected. IP:" + channel.getRemoteAddress());
        channel.register(selector, SelectionKey.OP_READ, "skjghksdhg");
        channel.write(ByteBuffer.wrap("Hello user!\n".getBytes(StandardCharsets.UTF_8)));
        channel.write(ByteBuffer.wrap("Enter --help for support info.\n".getBytes(StandardCharsets.UTF_8)));
    }


    private void handleRead(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        SocketAddress client = channel.getRemoteAddress();
        int readBytes = channel.read(buffer);

        clients.putIfAbsent(channel, "qwe");  //добавляю пользовотеля

        if (readBytes < 0) {
            channel.close();
            return;
        } else  if (readBytes == 0) {
            return;
        }

        buffer.flip();
        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            sb.append((char) buffer.get());
        }
        buffer.clear();

        // TODO: 21.06.2021
        // touch (filename) - создание файла
        // mkdir (dirname) - создание директории
        // cd (path | ~ | ..) - изменение текущего положения
        // rm (filename / dirname) - удаление файла / директории
        // copy (src) (target) - копирование файлов / директории
        // cat (filename) - вывод содержимого текстового файла
        // changenick (nickname) - изменение имени пользователя

        // добавить имя клиента

        if (key.isValid()) {
            String[] cmd = sb.toString()
                    .replace("\n", "")
                    .replace("\r", "")
                    .split(" ");
            if ("--help".equals(cmd[0])) {
                sendMessage(LS_COMMAND, selector, client);
                sendMessage(MKDIR_COMMAND, selector, client);
            } else if ("ls".equals(cmd[0])) {
                sendMessage(getFilesList().concat("\n"), selector, client);

                //создание файла
            } else if ("touch".equals(cmd[0])) {
                sendMessage(createFile(cmd[1],pathServer), selector, client);

                //создание директории
            } else if ("mkdir".equals(cmd[0])) {
                sendMessage(createDirectory(cmd[1],pathServer), selector, client);

                //удаление
            }else if ("rm".equals(cmd[0])) {
                sendMessage(remove(cmd[1],pathServer), selector, client);

                //копирование
            }else if ("copy".equals(cmd[0])) {
                sendMessage(copy(cmd[1], pathServer, cmd[2]), selector, client);

                //чтение из файла
            } else if ("cat".equals(cmd[0])){
                sendMessage(readFile(cmd[1], pathServer), selector, client);

                //смена директории
            } else if ("cd".equals(cmd[0])) {
                sendMessage(changeDirectory(cmd[1]), selector, client);

                //смена имени пользователя
            } else if ("changenick".equals(cmd[0])){
                sendMessage(changeNick(cmd[1], client), selector, client);
            }
        }
    }


    private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.channel() instanceof SocketChannel) {
                if (((SocketChannel) key.channel()).getRemoteAddress().equals(client)) {
                    ((SocketChannel) key.channel()).write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
    }

    private String getFilesList() {
        String[] servers = new File("server").list();
        return String.join(" ", servers);
    }

    //Создание файла. Добавил проверку на существование файла.
    private String createFile(String fileName, Path pathServer) throws IOException {
        if(!Files.exists(Path.of(String.valueOf(pathServer), fileName))){
            Files.createFile(Path.of(String.valueOf(pathServer), fileName)) ;
            return "File " + fileName + " created.\n";
        }else {
            return "File " + fileName + " already exists\n";
        }
    }

    //Создание директории. Добавил проверку на существование директории.
    private String createDirectory(String dir, Path pathServer) throws IOException {
        if(!Files.exists(Path.of(String.valueOf(pathServer), dir))){
            Files.createDirectory(Path.of(String.valueOf(pathServer), dir));
            return "Directory " + dir + " created.\n";
        }else {
            return "Directory " + dir + " already exists\n";
        }
    }

    //Удаление. Добавил рекурсивное удаление папки с файлами.
    private String remove(String fileName, Path pathServer) throws IOException {
        Path pathRM = Path.of(pathServer + "/" + fileName);
        Files.walkFileTree(pathRM, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        return "File/directory " + fileName + " deleted.\n";
    }

    //Копирование
    private String copy(String fileName, Path pathServer, String target) throws IOException {
        Files.copy((Path.of(String.valueOf(pathServer), fileName)), (Path.of(String.valueOf(pathServer), target)));
        return "File/Directory " + fileName + " copy in " + target;
    }

    //Чтение из файла
    private String readFile(String fileName, Path pathServer) throws IOException {
        List<String> textFile = Files.readAllLines(Path.of(String.valueOf(pathServer), fileName), StandardCharsets.UTF_8);
        for (String s: textFile) {
            return s;
        }
        return "\n";
    }

    //смена дирректории
    private String changeDirectory(String cd) {
        if ("~".equals(cd)){
            pathServer = Path.of("server");
        } else if ("..".equals(cd)){
            pathServer = pathServer.getParent();
        } else{
            pathServer = Path.of(String.valueOf(pathServer), cd);
        }
        return "change directory " + pathServer;
    }

    //смена пользователя. Исправил смену имени пользователя
    private String changeNick(String name, SocketAddress client) {
        clients.replaceAll((k, v) -> name);
        return "New nickname " + name;

    }


    public static void main(String[] args) throws Exception {
        new NioTelnetServer();
    }
}