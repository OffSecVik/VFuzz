# VFuzz

![VFuzz](https://i.imgur.com/wGVMjPR.png)

VFuzz is a multithreaded, customizable web fuzzer designed to test the resilience of web applications. It works by fuzzing HTTP requests using various request methods (GET, POST, HEAD) and modes (VHOST, SUBDOMAIN, STANDARD). VFuzz is designed to handle high performance fuzzing tasks with accuracy.

## Table of Contents
- [Features](#Features)
- [Usage](#usage)
- [Command-line Arguments](#command-line-arguments)
- [Future Improvements](#future-improvements)
- [Limitations and Known Issues](#limitations-and-known-issues)
- [License](#license)
- [How to Contribute](#how-to-contribute)

## Features
**Request Fuzzing**: Supports multiple HTTP methods (GET, POST, HEAD) and fuzzing strategies (VHOST, SUBDOMAIN, etc.).

**Concurrency**: Multi-threaded execution for fast fuzzing using configurable thread counts.

**Recursion**: Automatically keeps fuzzing any discovered subdomain or subdirectory

**Metrics**: Tracks key metrics such as requests per second, retries, and successful requests, and dynamically adjusts based on performance.

**Request Modes**: Supports various request modes including standard, fuzz marker replacement, subdomain fuzzing, and VHOST fuzzing.

**Request File Parsing**: Capable of parsing requests from files for fuzzing specific HTTP requests.

**File extension fuzzing** Appends a user-supplied list of file extensions to the target url

## Usage
You can run VFuzz via the command line. The primary entry point is the VFuzz class, which accepts various command-line arguments to configure fuzzing behavior.

### Example usage
Recursively fuzz a site with a specified wordlist. The payloads will be appended.<br/>
``java -jar vfuzz.jar -u "http://example.com/" -w "/path/to/wordlist" --recursive``<br/>

Fuzz for subdomains via DNS queries<br/>
``java -jar vfuzz.jar -d "testdomain.com" -w "/path/to/wordlist"``<br/>

Use a custom marker to insert payloads while fuzzing for certain file extensions.<br/>
``java -jar vfuzz.jar -d "http://example.com/FUZZ/somedir" -w "/path/to/wordlist" -x ".html,.js,.txt``<br/>


## Command-line Arguments
| Argument               | Alias | Description                                                                                                                                   | Example                                                        |
|------------------------|-------|-----------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------|
| --help                 | -h    | Displays the help menu.                                                                                                                       | -h                                                             |
| --url                  | -u    | Target website URL.                                                                                                                           | -u "ht<span>tp://</span>example.com"                           |
| --wordlist             | -w    | Path to the wordlist                                                                                                                          | -w "/path/to/wordlist"                                         |
| --threads              | -t    | Number of threads (between 1 and 200).                                                                                                        | -t 10                                                          |
| --recursive            |       | Enables recursive fuzzing mode.                                                                                                               | --recursive                                                    |
| --method               |       | Specifies the HTTP method to use for requests.<br>Supported methods are GET, POST, and HEAD. Default is GET.                                  | --method "POST"                                                |
| --post-data            | -d    | Sets data to be used in POST request.<br>Automatically sets --method to "POST"                                                                | -d "some=data"                                                 |
| --cookie               | -C    | Sets custom cookies for the request.<br>Can be used multiple times for multiple cookies.                                                      | -C "username=JohnDoe"                                          |
| --header               | -H    | Sets custom headers for the requests.<br>Each header must be in the 'Name: Value' format.<br>Can be used multiple times for multiple headers. | -H "Content-Type: application/json"                            |
| --user-agent           | -A    | Sets the user agent for requests.                                                                                                             | --user-agent "Mozilla/5.0 (Windows NT 10.0; Win64; x64) [...]" |
| --random-agent         |       | Enables randomization of User-Agent header.                                                                                                   | --random-agent                                                 |
| --follow-redirects     |       | Makes VFuzz follow redirects.                                                                                                                 | --follow-redirects                                             |
| --exclude-length       | -l    | List of content lengths or length ranges to exclude, separated by commas.<br> Each length must be a valid integer.                            | -l "200,400-600"                                               |
| --exclude-result       | -E    | Excludes a result from being shown and used in recursive mode.                                                                                | -E "ht<span>tp://</span>donotfuzz.com/"                        |
| --exclude-status-codes | -e    | List of HTTP status codes or ranges to exclude, separated by commas. Default: 404                                                             | -e "404,405-410,505-560"                                       |
| --fuzz                 |       | Activates the FUZZ-marker fuzzing mode. Default marker is "FUZZ".                                                                             | --fuzz                                                         |
| --fuzz-marker          |       | Sets a custom FUZZ marker that will be replaced with the payload.<br>Using this will activate FUZZ mode.                                      | --fuzz-marker "HELLO-WORLD"                                    |
| --vhost                |       | Activates the virtual host fuzzing mode.                                                                                                      | --vhost                                                        |
| --subdomain            |       | Activates the subdomain fuzzing mode.                                                                                                         | --subdomain                                                    |
| --domain-name          | -D    | Sets the domain to fuzz with subdomain fuzzing mode.<br>Required in --subdomain mode.                                                         | -D "somedomain.com"                                            |
| --dns-server           |       | Provides a custom DNS server for use with subdomain mode.                                                                                     | --dns-server "1.2.3.4"                                         |
| --request-file         | -r    | Specifies the filepath to the HTTP request file for fuzzing.<br>This activates file-based fuzzing mode.                                       | -r "/path/to/requestfile.txt"                                  |
| --rate-limit           |       | Sets the maximum number of requests per second. Default is 4000. Provide "0" to disable rate limiting.                                        | --rate-limit 500                                               |
| --extensions           | -x    | List of file extensions, which will be appended to the target.<br>Can be provided with or without a leading dot.                              | -x ".php,.html,.txt"                                           |
| --ignore-case          |       | Makes the fuzzer case-insensitive.<br>Caution: Can lead to forking with recursion, depending on the wordlist.                                 | --ignore-case                                                  |

---

## Future Improvements
### Improved Logging
- provide a feature to save results to a specified file.
### Terminal Stability
- improve and thoroughly test the terminal output.
### Unit Tests
As of now, all the functionality was tested in CTF environments. While the program eventually performed as expected, we aim to write Unit Tests to ensure the Fuzzer behaves as expected.


## Limitations and known issues
### Inability to scroll in the terminal while the program is running
This is caused by using ANSI escape codes for the terminal output. We aim to find a suitable library to replace our home cooked output, and in the meantime to provide an alternative output option via a command line argument.
### Significant delay before receiving the first results
The way we currently handle response parsing means we can observe a delay between getting the responses for the first couple thousand requests, and then parsing said responses.
For some reason the parsing of the first couple thousand responses seems to happen simultaneously after a short delay.
From our testing, this does not affect the accuracy of the fuzzing. It only results in a jumpy progress bar.

## How to Contribute
- Fork this repository.
- Create a branch for your feature or fix.
- Commit your changes and open a pull request.