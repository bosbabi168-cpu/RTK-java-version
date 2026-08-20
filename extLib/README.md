# extLib — External Libraries

Semua library eksternal disimpan di folder ini (tanpa Maven).
`build.xml` dan `nbproject/project.xml` otomatis memasukkan semua `*.jar`
di folder ini ke classpath.

| File | Fungsi | Sumber |
|------|--------|--------|
| `mysql-connector-j-8.4.0.jar` | JDBC driver MySQL (pengganti `libmysqlclient` di versi C) | https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/ |
| `HikariCP-5.1.0.jar` | Connection pooling database (dipakai `common/Sql.java`, ganti koneksi tunggal versi C) | https://repo1.maven.org/maven2/com/zaxxer/HikariCP/5.1.0/ |
| `log4j-api-2.24.3.jar` | API logging Log4j2 | https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.24.3/ |
| `log4j-core-2.24.3.jar` | Implementasi/engine Log4j2 (rolling file appender, dll) | https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.24.3/ |
| `slf4j-api-2.0.13.jar` | Dibutuhkan HikariCP untuk logging internalnya | https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.13/ |
| `log4j-slf4j2-impl-2.24.3.jar` | Jembatan SLF4J → Log4j2, supaya log internal HikariCP ikut masuk ke file Log4j2 kita | https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-slf4j2-impl/2.24.3/ |
| `luaj-jse-3.0.1.jar` | Interpreter Lua murni Java (pengganti liblua5.1 + `sl.c`) — menjalankan 900+ skrip rtklua asli tanpa diubah, termasuk coroutine untuk dialog NPC | https://repo1.maven.org/maven2/org/luaj/luaj-jse/3.0.1/ |

Semua kebutuhan lain (MD5, zlib/deflate, CRC32, networking NIO) sudah
tersedia di Java SE standar, jadi tidak perlu library tambahan.

Konfigurasi Log4j2 ada di [`../resources/log4j2.xml`](../resources/log4j2.xml)
— `build.xml` menyalinnya ke `build/classes` (dan jadi bagian dari jar) supaya
otomatis terbaca saat startup.
