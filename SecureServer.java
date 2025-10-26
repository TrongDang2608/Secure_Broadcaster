import javax.net.ssl.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.SocketException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SecureServer.java
 * Một máy chủ GUI đa luồng, bảo mật SSL để gửi thông báo broadcast.
 */
public class SecureServer extends JFrame {

    // 1. Thành phần GUI
    private final JButton startButton;
    private final JButton stopButton;
    private final JButton broadcastButton;
    private final JTextField messageField;
    private final JTextArea logArea;

    // 2. Thành phần Mạng & Logic
    private volatile boolean isRunning = false; // Dùng 'volatile' để đảm bảo an toàn luồng
    private SSLServerSocket serverSocket;
    private final int PORT = 12345;

    // Danh sách các "bút" để ghi cho mỗi client. Phải được đồng bộ (synchronized)
    private final List<PrintWriter> clientWriters = Collections.synchronizedList(new ArrayList<>());

    // --- Constructor: Thiết lập toàn bộ GUI ---
    public SecureServer() {
        setTitle("Secure Broadcast Server");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Panel điều khiển (chứa các nút và ô nhập liệu)
        JPanel controlPanel = new JPanel();
        startButton = new JButton("Start");
        stopButton = new JButton("Stop");
        messageField = new JTextField(20);
        broadcastButton = new JButton("Gửi Broadcast");

        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        controlPanel.add(messageField);
        controlPanel.add(broadcastButton);
        add(controlPanel, BorderLayout.NORTH);

        // Khu vực Log (hiển thị nhật ký)
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);

        // --- Đăng ký sự kiện cho các nút ---

        // Nút Start
        startButton.addActionListener(e -> startServer());

        // Nút Stop
        stopButton.addActionListener(e -> stopServer());

        // Nút Gửi Broadcast
        broadcastButton.addActionListener(e -> broadcastMessage());

        // Cập nhật trạng thái nút ban đầu
        updateButtonStates(false);
        setVisible(true);
    }

    /**
     * Bắt đầu máy chủ.
     * Hàm này sẽ hỏi mật khẩu TRƯỚC, sau đó mới khởi động luồng
     */
    private void startServer() {
        // --- BƯỚC 1: LẤY MẬT KHẨU TỪ NGƯỜI DÙNG (TRÊN LUỒNG GUI) ---
        JPasswordField pf = new JPasswordField();
        pf.setEchoChar('*'); // Ẩn mật khẩu

        // 'this' là cửa sổ JFrame, hộp thoại sẽ hiện ra giữa cửa sổ
        int okCxl = JOptionPane.showConfirmDialog(
                this,
                pf,
                "Nhập Mật khẩu KeyStore Server:",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        // Nếu người dùng nhấn "Cancel" hoặc đóng hộp thoại
        if (okCxl != JOptionPane.OK_OPTION) {
            log("Đã hủy khởi động. Người dùng không nhập mật khẩu.");
            return; // Dừng, không làm gì cả
        }

        // Lấy mật khẩu và đánh dấu 'final' để dùng trong luồng mới
        final char[] password = pf.getPassword();

        // --- BƯỚC 2: KHỞI ĐỘNG SERVER TRONG LUỒNG MỚI (VỚI MẬT KHẨU ĐÃ LẤY) ---
        new Thread(() -> {
            try {
                log("Đang khởi động máy chủ...");
                updateButtonStates(true); // Cập nhật GUI

                // --- PHẦN QUAN TRỌNG: THIẾT LẬP SSL ---

                // Mật khẩu LẤY TỪ NGƯỜI DÙNG
                char[] keystorePassword = password;
                char[] keyPassword = password; // Chúng ta dùng chung 1 mật khẩu

                // 1. Tải KeyStore
                KeyStore ks = KeyStore.getInstance("JKS");
                FileInputStream fis = new FileInputStream("server.jks"); // File chúng ta vừa tạo
                ks.load(fis, keystorePassword);

                // 2. Tạo KeyManagerFactory
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, keyPassword);

                // 3. Tạo SSLContext
                SSLContext sslContext = SSLContext.getInstance("TLS"); // Dùng giao thức TLS
                sslContext.init(kmf.getKeyManagers(), null, null);

                // 4. Tạo SSLServerSocketFactory
                SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();

                // 5. Tạo SSLServerSocket (thay vì ServerSocket)
                serverSocket = (SSLServerSocket) ssf.createServerSocket(PORT);

                // --- KẾT THÚC THIẾT LẬP SSL ---

                isRunning = true;
                log("Máy chủ SSL đã khởi động trên cổng: " + PORT);

                // Vòng lặp chính: Chấp nhận kết nối
                while (isRunning) {
                    try {
                        // (Phần code accept client giữ nguyên)
                        SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                        log("Client đã kết nối: " + clientSocket.getInetAddress());

                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                        clientWriters.add(writer);

                        ClientHandler handler = new ClientHandler(clientSocket, writer);
                        new Thread(handler).start();

                    } catch (SocketException se) {
                        if (isRunning) {
                            log("Lỗi Socket: " + se.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                // Nếu có lỗi (ví dụ sai mật khẩu), báo lỗi và reset nút
                log("Lỗi nghiêm trọng khi khởi động máy chủ: " + e.getMessage());
                e.printStackTrace();
                updateButtonStates(false); // Đặt lại trạng thái nút
            } finally {
                // [BẢO MẬT] Xóa mật khẩu khỏi bộ nhớ ngay sau khi dùng xong
                java.util.Arrays.fill(password, ' ');
            }
        }).start();
    }

    /**
     * Dừng máy chủ một cách an toàn
     */
    private void stopServer() {
        if (!isRunning) return;

        try {
            log("Đang dừng máy chủ...");
            isRunning = false;

            // Đóng tất cả kết nối của client
            // Phải dùng vòng lặp `synchronized` khi thao tác với danh sách
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    try {
                        writer.close();
                    } catch (Exception e) {
                        // Bỏ qua lỗi
                    }
                }
                clientWriters.clear();
            }

            // Đóng ServerSocket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            log("Máy chủ đã dừng.");
            updateButtonStates(false);

        } catch (IOException e) {
            log("Lỗi khi dừng máy chủ: " + e.getMessage());
        }
    }

    /**
     * Gửi một tin nhắn đến tất cả các client đang kết nối
     */
    private void broadcastMessage() {
        String message = messageField.getText();
        if (message.isEmpty() || !isRunning) {
            return;
        }

        log("BROADCAST: " + message);

        // Phải dùng `synchronized` khi duyệt danh sách
        synchronized (clientWriters) {
            for (PrintWriter writer : clientWriters) {
                writer.println(message);
                writer.flush(); // Đẩy tin đi ngay
            }
        }
        messageField.setText(""); // Xóa ô nhập liệu
    }

    /**
     * Ghi nhật ký ra JTextArea (một cách an toàn từ các luồng khác)
     */
    private void log(String message) {
        // Swing không an toàn luồng, phải cập nhật GUI trên Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength()); // Tự cuộn xuống
        });
    }

    /**
     * Cập nhật trạng thái Bật/Tắt của các nút
     */
    private void updateButtonStates(boolean serverIsActive) {
        startButton.setEnabled(!serverIsActive);
        stopButton.setEnabled(serverIsActive);
        broadcastButton.setEnabled(serverIsActive);
        messageField.setEnabled(serverIsActive);
    }

    // --- Lớp nội bộ (Inner Class) để xử lý từng client ---
    private class ClientHandler implements Runnable {
        private final SSLSocket clientSocket;
        private final PrintWriter writer;
        private BufferedReader reader;

        public ClientHandler(SSLSocket socket, PrintWriter writer) {
            this.clientSocket = socket;
            this.writer = writer;
            try {
                // Tạo một "tai nghe" để nhận dữ liệu từ client
                this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                log("Lỗi khi tạo ClientHandler: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                // Client của chúng ta chỉ nhận (receive)
                // Chúng ta dùng vòng lặp readLine() để phát hiện khi client ngắt kết nối
                // Khi client đóng, readLine() sẽ trả về null
                String inputLine;
                while ((inputLine = reader.readLine()) != null) {
                    // Hiện tại client không gửi gì, nhưng ta để đây để mở rộng
                    // log("Client " + clientSocket.getInetAddress() + " nói: " + inputLine);
                }
            } catch (SocketException e) {
                if (isRunning) {
                    log("Client " + clientSocket.getInetAddress() + " ngắt kết nối đột ngột.");
                }
            } catch (IOException e) {
                if (isRunning) {
                    log("Lỗi I/O với client " + clientSocket.getInetAddress() + ": " + e.getMessage());
                }
            } finally {
                // --- Phần dọn dẹp quan trọng ---
                log("Client " + clientSocket.getInetAddress() + " đã ngắt kết nối.");

                // Xóa "bút" của client này khỏi danh sách broadcast
                clientWriters.remove(writer);

                // Đóng luồng và socket
                try {
                    if (writer != null) writer.close();
                    if (reader != null) reader.close();
                    if (clientSocket != null) clientSocket.close();
                } catch (IOException e) {
                    log("Lỗi khi đóng tài nguyên của client: " + e.getMessage());
                }
            }
        }
    }

    // --- Hàm main để khởi chạy Server ---
    public static void main(String[] args) {
        // Đảm bảo GUI được tạo trên Event Dispatch Thread
        SwingUtilities.invokeLater(SecureServer::new);
    }
}