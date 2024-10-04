# VFuzz

![VFuzz](https://i.imgur.com/wGVMjPR.png)

VFuzz is a multi-threaded, customizable web fuzzer designed to test the resilience of web applications. It works by fuzzing HTTP requests using various request methods (GET, POST, HEAD) and modes (VHOST, SUBDOMAIN, STANDARD). VFuzz is designed to handle high performance fuzzing tasks with accuracy.

## Features
**Request Fuzzing**: Supports multiple HTTP methods (GET, POST, HEAD) and fuzzing strategies (VHOST, SUBDOMAIN, etc.).

**Concurrency**: Multi-threaded execution for fast fuzzing using configurable thread counts.

**Recursion**: Automatically keeps fuzzing any discovered subdomain or subdirectory

**Metrics**: Tracks key metrics such as requests per second, retries, and successful requests, and dynamically adjusts based on performance.

**Dynamic Request Modes**: Supports various request modes including standard, fuzz marker replacement, subdomain fuzzing, and VHOST fuzzing.

**Request File Parsing**: Capable of parsing complex requests from files for fuzzing specific HTTP requests.

## Usage
You can run VFuzz via the command line. The primary entry point is the VFuzz class, which accepts various command-line arguments to configure fuzzing behavior.

### List of command line arguments

``--help`` ``-h``
Displays the help menu.

``--url`` ``-u``
[required] URL to the target website. This argument is required and must start with http:// or https://. Trailing slashes are automatically removed.

``--wordlist`` ``-w``
[required] Path to the wordlist.

``--threads`` ``-t``
Number of threads. Must be a number between 1 and 200.

``-C`` `` --cookie``
Sets custom cookies for the requests. Can be used multiple times for multiple cookies. Example: -C "username=JohnDoe"

``-H``
Sets custom headers for the requests. Each header must be in the 'Name: Value' format. Can be used multiple times for multiple headers. Example: -H "Content-Type: application/json"

``--excludeLength`` ``-l``
List of content lengths or length ranges to exclude, separated by commas. Each length must be a valid integer.

``--excludeResult`` `` -E``
Results to exclude from being shown and used in recursive mode.

``--recursive``
Enables recursive fuzzing mode.

``--method``
Specifies the HTTP method to use for requests. Supported methods are GET, POST, and HEAD. Default is GET.

``-d`` ``--post-data``
Sets data to be used in POST request. Automatically sets --method to "POST"

``--random-agent``
Enables randomization of User-Agent header.

``--excludeStatusCodes`` ``-e``
List of HTTP status codes or ranges to exclude, separated by commas. For example: 404,405-410,505-560. Each code or range must be valid.
Default: 404

``--fuzz``
Activates the FUZZ-marker fuzzing mode.

``--fuzz-marker``
Specifies the fuzz marker within the request file that will be replaced with dynamic content. Example: --fuzz-marker "FUZZ"
Default: FUZZ

``--vhost``
Activates the virtual host fuzzing mode.

``--subdomain``
Activates the subdomain fuzzing mode.

``-r``
Specifies the filepath to the HTTP request file for fuzzing. This activates file-based fuzzing mode. Ensure the file exists. Example: -r "/path/to/requestfile.txt"

``--follow-redirects``
Makes VFuzz follow redirects.

``--max-retries``
Specifies the maximum number of retries for a request. This value must be an integer. Default is 5.
Default: 5

``--user-agent`` ``-A``
Sets the user agent for requests. Example: --user-agent "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3"

``--rate-limit``
Sets the maximum number of requests per second. This value must be a positive integer. Default is 4000.

``--metrics``
Enables metrics collection.

## Limitations and known issues
The fuzzer is very fast and hast crashed test servers in the past. Since rate limiting is not yet implemented, we recommend not using this program against weak servers.
Further, as of now there is no way to save the output to a file.

## Future Improvements
- Dynamic Rate Limiting, which uses metrics to react dynamically to the response rate of a server
- Logging results into a file