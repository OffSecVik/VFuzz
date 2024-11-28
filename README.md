<p align="center">
  <img src="https://i.imgur.com/wGVMjPR.png" alt="VFuzz Logo"/>
</p>

<h1 align="center">🔍 VFuzz 🔍</h1>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache-blue.svg" alt="License"></a>
  <a href="https://github.com/VFuzz/VFuzz/releases"><img src="https://img.shields.io/badge/version-1.0.0-green.svg" alt="Version"></a>
  <a href="https://www.java.com/"><img src="https://img.shields.io/badge/Java-17%2B-blue.svg" alt="Java"></a>
  <a href="https://github.com/OffSecVik/VFuzz/releases"><img src="https://img.shields.io/github/downloads/OffSecVik/VFuzz/total.svg" alt="Downloads"></a>
  <a href="https://github.com/OffSecVik/VFuzz/issues"><img src="https://img.shields.io/github/issues/OffSecVik/VFuzz.svg" alt="Open Issues"></a>
  <a href="https://github.com/OffSecVik/VFuzz/stargazers"><img src="https://img.shields.io/github/stars/OffSecVik/VFuzz.svg" alt="Stars"></a>
  <a href="https://github.com/OffSecVik/VFuzz/graphs/contributors"><img src="https://img.shields.io/github/contributors/OffSecVik/VFuzz.svg" alt="Contributors"></a>
  <a href="https://github.com/OffSecVik/VFuzz/commits"><img src="https://img.shields.io/github/last-commit/OffSecVik/VFuzz.svg" alt="Last Commit"></a>
</p>

<p align="center">⚡ A high-performance, multithreaded web fuzzer designed to test the resilience of web applications. ⚡</p>


---

## 📖 Table of Contents
- [✨ Features](#-features)
- [⚙️ Usage](#%EF%B8%8F-usage)
  - [🔄 Recursive Fuzzing](#-recursive-fuzzing)
  - [🌐 Subdomain Fuzzing](#-subdomain-fuzzing)
  - [🎯 FUZZ Marker Mode](#-fuzz-marker-mode)
  - [📑 Example Usage](#-example-usage)
- [💻 Command-line Arguments](#-command-line-arguments)
- [🚀 Future Improvements](#-future-improvements)
- [🐞 Limitations and Known Issues](#-limitations-and-known-issues)
- [📜 License](#-license)
- [🤝 Contributing](#-contributing)

---

## ✨ Features
- 🔍 **Request Fuzzing**: Supports multiple HTTP methods (GET, POST, HEAD) and fuzzing strategies (VHOST, SUBDOMAIN, etc.).
- ⚡ **Concurrency**: Multi-threaded execution for fast fuzzing using configurable thread counts.
- 🔄 **Recursion**: Automatically keeps fuzzing any discovered subdomain or subdirectory.
- 📊 **Metrics**: Tracks key metrics such as requests per second, retries, and successful requests.
- ⚙️ **Request Modes**: Includes standard requests, fuzz marker replacement, subdomain fuzzing, and VHOST fuzzing.
- 📄 **Request File Parsing**: Parses requests from files for fuzzing specific HTTP requests.
- 🧩 **File Extension Fuzzing**: Appends a user-supplied list of file extensions to the target URL.

---

## ⚙️ Usage

> **⚠️ Warning:** Ensure you have explicit permission from the target system owner before using VFuzz. Unauthorized testing may violate laws.

Run VFuzz from the command line. Below are some examples to get you started:

### 🔄 Recursive Fuzzing:
```bash
java -jar vfuzz.jar -u "http://example.com/" -w "/path/to/wordlist" --recursive
```

<p>Automatically fuzzes discovered subdirectories.</p>


### 🌐 Subdomain Fuzzing:
```bash
java -jar vfuzz.jar -d "testdomain.com" -w "/path/to/wordlist"
```

<p>Identifies subdomains for the specified domain.</p>



### 🎯 FUZZ Marker Mode:
```
java -jar vfuzz.jar -d "http://example.com/FUZZ/somedir" -w "/path/to/wordlist" -x ".html,.js,.txt
```

<p>Replaces FUZZ marker with payloads and appends file extensions.</p>


<br>

> **💡 Tip:** Use high-quality wordlists from resources like SecLists for better results.


<br>


### 📑 Example Usage:


<img src="https://github.com/user-attachments/assets/6f522643-514b-454a-bd01-9c9557fc20c3" width="750">


> **🔍 What’s shown here?** This output demonstrates VFuzz identifying directories recursively. Customize wordlists to improve accuracy.


---

## 💻 Command-line Arguments

### 🛠️ Core Options
<details>
<summary>Click to expand</summary>

| **Argument**           | **Alias** | **Description**                                                                 | **Example**                         |
|-------------------------|-----------|---------------------------------------------------------------------------------|-------------------------------------|
| `--help`               | `-h`      | Displays the help menu.                                                         | `-h`                                |
| `--url`                | `-u`      | Target website URL.                                                             | `-u "http://example.com"`           |
| `--wordlist`           | `-w`      | Path to the wordlist.                                                           | `-w "/path/to/wordlist"`            |
| `--threads`            | `-t`      | Number of threads (between 1 and 200).                                          | `-t 10`                             |
</details>

---

### 🎯 Fuzzing Modes
<details>
<summary>Click to expand</summary>

| **Argument**           | **Alias** | **Description**                                                                 | **Example**                         |
|-------------------------|-----------|---------------------------------------------------------------------------------|-------------------------------------|
| `--recursive`          |           | Enables recursive fuzzing mode.                                                 | `--recursive`                       |
| `--fuzz`               |           | Activates the FUZZ-marker fuzzing mode. Default marker is "FUZZ".               | `--fuzz`                            |
| `--fuzz-marker`        |           | Sets a custom FUZZ marker that will be replaced with the payload.               | `--fuzz-marker "HELLO-WORLD"`       |
| `--vhost`              |           | Activates the virtual host fuzzing mode.                                        | `--vhost`                           |
| `--subdomain`          |           | Activates the subdomain fuzzing mode.                                           | `--subdomain`                       |
| `--domain-name`        | `-D`      | Sets the domain to fuzz with subdomain mode. Required in `--subdomain` mode.    | `-D "somedomain.com"`               |
| `--dns-server`         |           | Provides a custom DNS server for use with subdomain mode.                       | `--dns-server "1.2.3.4"`            |

> **💡 Pro Tip:** Use custom markers like "FUZZ" in URLs or file paths for targeted payload injection.

</details>

---

### ⚙️ HTTP Configuration
<details>
<summary>Click to expand</summary>

| **Argument**           | **Alias** | **Description**                                                                 | **Example**                         |
|-------------------------|-----------|---------------------------------------------------------------------------------|-------------------------------------|
| `--method`             |           | Specifies the HTTP method to use for requests.<br>Supported methods: GET, POST, HEAD. Default: GET. | `--method "POST"`          |
| `--post-data`          | `-d`      | Sets data to be used in POST request. Automatically sets `--method` to "POST".  | `-d "some=data"`                    |
| `--cookie`             | `-C`      | Sets custom cookies for the request. Can be used multiple times.                | `-C "username=JohnDoe"`             |
| `--header`             | `-H`      | Sets custom headers for requests. Each header must be in the 'Name: Value' format. Can be used multiple times. | `-H "Content-Type: application/json"` |
| `--user-agent`         | `-A`      | Sets the user agent for requests.                                               | `--user-agent "Mozilla/5.0 [...]"`  |
| `--random-agent`       |           | Enables randomization of the User-Agent header.                                 | `--random-agent`                    |
| `--follow-redirects`   |           | Makes VFuzz follow redirects.                                                   | `--follow-redirects`                |
</details>

---

### 🧹 Filters and Exclusions
<details>
<summary>Click to expand</summary>

| **Argument**           | **Alias** | **Description**                                                                 | **Example**                         |
|-------------------------|-----------|---------------------------------------------------------------------------------|-------------------------------------|
| `--exclude-length`     | `-l`      | List of content lengths or length ranges to exclude, separated by commas. Each length must be a valid integer. | `-l "200,400-600"`      |
| `--exclude-result`     | `-E`      | Excludes a result from being shown and used in recursive mode.                   | `-E "http://donotfuzz.com/"`        |
| `--exclude-status-codes`| `-e`      | List of HTTP status codes or ranges to exclude, separated by commas. Default: 404. | `-e "404,405-410,505-560"`        |
</details>

---

### 📄 File Input and Output
<details>
<summary>Click to expand</summary>

| **Argument**           | **Alias** | **Description**                                                                 | **Example**                         |
|-------------------------|-----------|---------------------------------------------------------------------------------|-------------------------------------|
| `--request-file`       | `-r`      | Specifies the filepath to the HTTP request file for fuzzing. This activates file-based fuzzing mode. | `-r "/path/to/requestfile.txt"` |
| `--extensions`         | `-x`      | List of file extensions to append to the target. Can be provided with or without a leading dot. | `-x ".php,.html,.txt"` |
</details>

---

### ⚡ Performance Settings
<details>
<summary>Click to expand</summary>

| **Argument**           | **Alias** | **Description**                                                                 | **Example**                         |
|-------------------------|-----------|---------------------------------------------------------------------------------|-------------------------------------|
| `--rate-limit`         |           | Sets the maximum number of requests per second. Default: 4000. Provide "0" to disable rate limiting. | `--rate-limit 500`         |
| `--ignore-case`        |           | Makes the fuzzer case-insensitive.<br>Caution: Can lead to recursion issues depending on the wordlist. | `--ignore-case`          |

> **ℹ️ Note:** The `--rate-limit` argument can significantly impact performance. A value of "0" disables rate limiting entirely, which may overwhelm some servers.

</details>

---

## 🚀 Future Improvements
- 📁 **Improved Logging**: Add options to save results in JSON, text, or CSV format.
- 🎨 **Enhanced Terminal Output**: Improve stability and scrolling behavior for better usability.
- ✅ **Unit Testing**: Write unit tests for core functionality.

> **💡 Got an idea?** We'd love to hear your suggestions for new features! Submit your ideas [here](https://github.com/OffSecVik/VFuzz/issues).

---

## 🐞 Limitations and Known Issues
- 🖥️ **Terminal Scrolling**: ANSI escape codes prevent scrolling while running. Future releases will offer alternative output options.
- ⏱️ **Initial Delay**: Parsing of the first few thousand responses may be delayed due to simultaneous processing.

---

## 📜 License
This project is licensed under the Apache License. See the [LICENSE](LICENSE) file for details.

> **📜 Reminder:** VFuzz is a powerful tool. Always use it responsibly and in compliance with applicable laws and ethical guidelines.

---

## 🤝 Contributing

We welcome contributions! If you want to contribute, please follow these steps:
1. **Fork the repository**.
2. **Create a new branch** (`git checkout -b feature-branch`).
3. **Commit your changes** (`git commit -am 'Add new feature'`).
4. **Push to the branch** (`git push origin feature-branch`).
5. **Open a pull request.**


---

Made with ❤️
