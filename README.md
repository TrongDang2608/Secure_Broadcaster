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
