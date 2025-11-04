
# Dự án SecureBroadcaster (Server/Client SSL Java)

Đây là một ứng dụng chat broadcast đa client đơn giản bằng Java, sử dụng GUI (Swing) và bảo mật SSL/TLS.

## 1. Yêu cầu hệ thống
* JDK 11 trở lên (Dự án được viết với JDK 21).

## 2. Cài đặt (BẮT BUỘC)

Dự án này sử dụng SSL và yêu cầu một file Java KeyStore (JKS) tên là `server.jks`. Vì lý do bảo mật, file này **không** được đưa lên Git. Bạn phải tự tạo ra nó.

### Bước 1: Tạo file Keystore
Mở terminal tại thư mục gốc của dự án và chạy lệnh sau:

```bash
keytool -genkeypair -alias serveralias -keyalg RSA -keysize 2048 -storetype JKS -keystore server.jks -validity 365 
```

### Trong quá trình tạo:

* Enter keystore password:: Nhập một mật khẩu (ví dụ: 123123).

* What is your first and last name?: Gõ localhost.

* Các câu hỏi còn lại: Có thể bỏ qua (nhấn Enter) hoặc điền tùy ý.

* Is... correct?: Gõ yes.

* Enter key password for <serveralias>...: Nhấn Enter (để dùng chung mật khẩu).

### QUAN TRỌNG: Mật khẩu bạn tạo ở đây (ví dụ: 123123) chính là mật khẩu bạn sẽ phải nhập vào hộp thoại popup khi chạy ứng dụng.

### Bước 2: Biên dịch mã nguồn

Sau khi đã có file server.jks, biên dịch toàn bộ code:

```bash
mkdir classes
javac -d classes *.java
```

## 3. Cách chạy ứng dụng

Chạy Server

Mở terminal 1:

```bash
java -cp classes SecureServer
```

* Nhấn nút "Start".

* Nhập mật khẩu Keystore bạn đã tạo ở trên.

* Server sẽ khởi động.

## Chạy Client:

Mở terminal 2 (và 3, 4...):

```bash
java -cp classes SecureClient
```

* Nhấn nút "Kết nối".

* Nhập cùng một mật khẩu Keystore.

* Client sẽ kết nối thành công.
avfhabsdjbadjnasd
