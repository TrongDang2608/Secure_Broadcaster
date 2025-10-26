import javax.net.ssl.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyStore;

/**
 * SecureClient.java
 * Một máy khách GUI, kết nối SSL để nhận thông báo broadcast.
 */
public class SecureClient extends JFrame {

    // 1. Thành phần GUI
    private final JButton connectButton;
    private final JButton disconnectButton;
    private final JTextField ipField;
    private final JTextField portField;
    private final JTextArea messageArea;

    // 2. Thành phần Mạng & Logic
    private volatile boolean isConnected = false;
    private SSLSocket sslSocket;
    private BufferedReader reader;

    // Một luồng riêng chỉ để lắng nghe tin nhắn từ server
    private Thread listeningThread;

    // --- Constructor: Thiết lập toàn bộ GUI ---
    public SecureClient() {
        setTitle("Secure Broadcast Client");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Panel điều khiển (chứa các nút và ô nhập liệu)
        JPanel controlPanel = new JPanel();
        ipField = new JTextField("localhost", 15); // Mặc định là localhost
        portField = new JTextField("12345", 5);    // Mặc định cổng 12345
        connectButton = new JButton("Kết nối");
        disconnectButton = new JButton("Ngắt kết nối");

        controlPanel.add(new JLabel("Server IP:"));
        controlPanel.add(ipField);
        controlPanel.add(new JLabel("Cổng:"));
        controlPanel.add(portField);
        controlPanel.add(connectButton);
        controlPanel.add(disconnectButton);
        add(controlPanel, BorderLayout.NORTH);

        // Khu vực hiển thị tin nhắn
        messageArea = new JTextArea();
        messageArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(messageArea);
        add(scrollPane, BorderLayout.CENTER);

        // --- Đăng ký sự kiện cho các nút ---
        connectButton.addActionListener(e -> connectToServer());
        disconnectButton.addActionListener(e -> disconnectFromServer());

        // Cập nhật trạng thái nút ban đầu
        updateButtonStates(false);
        setVisible(true);
    }

    /**
     * Kết nối đến máy chủ.
     * Sẽ hỏi mật khẩu TrustStore TRƯỚC, sau đó mới kết nối
     */
    private void connectToServer() {
        String host = ipField.getText();
        int port;
        try {
            port = Integer.parseInt(portField.getText());
        } catch (NumberFormatException e) {
            log("Lỗi: Cổng phải là một con số.");
            return;
        }

        // --- BƯỚC 1: LẤY MẬT KHẨU TỪ NGƯỜI DÙNG (TRÊN LUỒNG GUI) ---
        JPasswordField pf = new JPasswordField();
        pf.setEchoChar('*');

        int okCxl = JOptionPane.showConfirmDialog(
                this,
                pf,
                "Nhập Mật khẩu TrustStore (server.jks):",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (okCxl != JOptionPane.OK_OPTION) {
            log("Đã hủy kết nối.");
            return;
        }

        final char[] password = pf.getPassword();

        // --- BƯỚC 2: KHỞI ĐỘNG KẾT NỐI TRÊN LUỒNG MỚI ---
        new Thread(() -> {
            try {
                log("Đang kết nối tới " + host + ":" + port + "...");
                updateButtonStates(true);

                // --- PHẦN QUAN TRỌNG: THIẾT LẬP SSL CLIENT ---

                // Mật khẩu LẤY TỪ NGƯỜI DÙNG
                char[] keystorePassword = password;

                // 1. Tải TrustStore (Kho tin cậy)
                KeyStore ts = KeyStore.getInstance("JKS");
                FileInputStream fis = new FileInputStream("server.jks");
                ts.load(fis, keystorePassword);

                // 2. Tạo TrustManagerFactory
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ts);

                // 3. Tạo SSLContext
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), null); // Chỉ cần TrustManager

                // 4. Tạo SSLSocketFactory
                SSLSocketFactory ssf = sslContext.getSocketFactory();

                // 5. Tạo SSLSocket (thay vì Socket)
                sslSocket = (SSLSocket) ssf.createSocket(host, port);

                // 6. [QUAN TRỌNG] Bắt đầu "Bắt tay" (Handshake)
                sslSocket.startHandshake();

                // --- KẾT THÚC THIẾT LẬP SSL ---

                isConnected = true;
                log("Đã kết nối bảo mật tới máy chủ.");

                // Tạo "tai nghe" để nhận dữ liệu từ server
                reader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream()));

                // Bắt đầu một luồng riêng chỉ để lắng nghe tin nhắn
                startListening();

            } catch (Exception e) {
                // Nếu lỗi (sai mật khẩu, server sập...)
                log("Lỗi kết nối: " + e.getMessage());
                e.printStackTrace();
                updateButtonStates(false); // Đặt lại trạng thái nút
            } finally {
                // [BẢO MẬT] Xóa mật khẩu khỏi bộ nhớ
                java.util.Arrays.fill(password, ' ');
            }
        }).start();
    }

    /**
     * Bắt đầu luồng lắng nghe tin nhắn từ Server
     */
    private void startListening() {
        listeningThread = new Thread(() -> {
            try {
                String serverMessage;
                // Vòng lặp này sẽ chạy miễn là kết nối còn
                while (isConnected && (serverMessage = reader.readLine()) != null) {
                    log("Server: " + serverMessage);
                }
            } catch (SocketException e) {
                if (isConnected) {
                    log("Lỗi: Mất kết nối tới máy chủ.");
                }
                // Nếu isConnected = false, đây là do ta chủ động ngắt kết nối
            } catch (IOException e) {
                if (isConnected) {
                    log("Lỗi I/O: " + e.getMessage());
                }
            } finally {
                // Dù kết thúc thế nào, hãy đảm bảo trạng thái GUI được cập nhật
                if (isConnected) {
                    // Bị ngắt kết nối ngoài ý muốn
                    log("Đã ngắt kết nối.");
                    updateButtonStates(false);
                    isConnected = false;
                }
            }
        });
        listeningThread.start();
    }

    /**
     * Ngắt kết nối khỏi máy chủ
     */
    private void disconnectFromServer() {
        if (!isConnected) return;

        try {
            log("Đang ngắt kết nối...");
            isConnected = false;

            // Đóng socket sẽ khiến reader.readLine() trong luồng listeningThread
            // ném ra một SocketException, làm cho luồng đó kết thúc
            if (sslSocket != null && !sslSocket.isClosed()) {
                sslSocket.close(); // Đóng socket
            }
            if (reader != null) {
                reader.close();
            }

            // Chờ luồng lắng nghe kết thúc
            if (listeningThread != null) {
                listeningThread.join(1000); // Chờ tối đa 1 giây
            }

        } catch (IOException e) {
            log("Lỗi khi đóng socket: " + e.getMessage());
        } catch (InterruptedException e) {
            log("Lỗi khi chờ luồng: " + e.getMessage());
        } finally {
            log("Đã ngắt kết nối.");
            updateButtonStates(false);
        }
    }

    /**
     * Ghi nhật ký ra JTextArea (một cách an toàn từ các luồng khác)
     */
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            messageArea.append(message + "\n");
            messageArea.setCaretPosition(messageArea.getDocument().getLength()); // Tự cuộn xuống
        });
    }

    /**
     * Cập nhật trạng thái Bật/Tắt của các nút
     */
    private void updateButtonStates(boolean clientIsActive) {
        connectButton.setEnabled(!clientIsActive);
        disconnectButton.setEnabled(clientIsActive);
        ipField.setEnabled(!clientIsActive);
        portField.setEnabled(!clientIsActive);
    }

    // --- Hàm main để khởi chạy Client ---
    public static void main(String[] args) {
        // Đảm bảo GUI được tạo trên Event Dispatch Thread
        SwingUtilities.invokeLater(SecureClient::new);
    }
}