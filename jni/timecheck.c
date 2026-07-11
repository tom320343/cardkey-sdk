#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <time.h>
#include <android/log.h>
#include "timecheck.h"

#define TAG "mthugo_time"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

static int http_get(const char *host, const char *path, char **response_out) {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return -1;

    struct hostent *he = gethostbyname(host);
    if (!he) { close(sock); return -1; }

    struct sockaddr_in addr;
    addr.sin_family = AF_INET;
    addr.sin_port = htons(443);
    memcpy(&addr.sin_addr, he->h_addr_list[0], he->h_length);

    if (connect(sock, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        close(sock); return -1;
    }

    char req[1024];
    snprintf(req, sizeof(req),
        "HEAD %s HTTP/1.1\r\nHost: %s\r\nConnection: close\r\n\r\n",
        path, host);
    send(sock, req, strlen(req), 0);

    char *buf = malloc(8192);
    if (!buf) { close(sock); return -1; }
    int total = 0;
    int n;
    while ((n = recv(sock, buf + total, 8192 - total - 1, 0)) > 0) {
        total += n;
        if (total >= 8190) break;
    }
    buf[total] = '\0';
    close(sock);

    *response_out = buf;
    return total;
}

static int http_get_full(const char *host, const char *path, char **response_out) {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return -1;

    struct hostent *he = gethostbyname(host);
    if (!he) { close(sock); return -1; }

    struct sockaddr_in addr;
    addr.sin_family = AF_INET;
    addr.sin_port = htons(443);
    memcpy(&addr.sin_addr, he->h_addr_list[0], he->h_length);

    if (connect(sock, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        close(sock); return -1;
    }

    char req[2048];
    snprintf(req, sizeof(req),
        "GET %s HTTP/1.1\r\nHost: %s\r\nConnection: close\r\n\r\n",
        path, host);
    send(sock, req, strlen(req), 0);

    char *buf = malloc(32768);
    if (!buf) { close(sock); return -1; }
    int total = 0;
    int n;
    while ((n = recv(sock, buf + total, 32768 - total - 1, 0)) > 0) {
        total += n;
        if (total >= 32766) break;
    }
    buf[total] = '\0';
    close(sock);

    *response_out = buf;
    return total;
}

static jlong parse_date_header(const char *response) {
    const char *p = strstr(response, "Date: ");
    if (!p) p = strstr(response, "date: ");
    if (!p) return -1;

    p += 6;
    struct tm tm = {0};
    char mon[4] = {0};
    int day, year, hour, min, sec;

    if (sscanf(p, "%*[^,], %d %3s %d %d:%d:%d",
               &day, mon, &year, &hour, &min, &sec) != 6) {
        return -1;
    }

    const char *months[] = {"Jan","Feb","Mar","Apr","May","Jun",
                            "Jul","Aug","Sep","Oct","Nov","Dec", NULL};
    for (int i = 0; months[i]; i++) {
        if (strncasecmp(mon, months[i], 3) == 0) {
            tm.tm_mon = i;
            break;
        }
    }
    tm.tm_mday = day;
    tm.tm_year = year - 1900;
    tm.tm_hour = hour;
    tm.tm_min = min;
    tm.tm_sec = sec;

    time_t gmt = timegm(&tm);
    return (jlong)gmt * 1000 + 8 * 3600000;
}

jlong fetch_beijing_time(void) {
    const char *hosts[] = {"www.baidu.com", "www.qq.com", "www.taobao.com", NULL};
    const char *paths[] = {"/", "/", "/", NULL};

    for (int i = 0; hosts[i]; i++) {
        char *resp = NULL;
        int len = http_get(hosts[i], paths[i], &resp);
        if (len > 0 && resp) {
            jlong t = parse_date_header(resp);
            free(resp);
            if (t > 0) return t;
        }
    }
    return -1;
}

int check_expiry(const char *expiry_str, jlong beijing_time) {
    if (!expiry_str || beijing_time <= 0) return 0;

    const char *dash = strrchr(expiry_str, '-');
    if (!dash) return 0;
    const char *date = dash + 1;

    if (strlen(date) != 12) return 0;

    int year, month, day, hour, min;
    if (sscanf(date, "%4d%2d%2d%2d%2d", &year, &month, &day, &hour, &min) != 5) return 0;

    struct tm tm = {0};
    tm.tm_year = year - 1900;
    tm.tm_mon = month - 1;
    tm.tm_mday = day;
    tm.tm_hour = hour;
    tm.tm_min = min;
    tm.tm_sec = 59;

    time_t expiry = timegm(&tm);
    jlong expiry_ms = (jlong)expiry * 1000 + 8 * 3600000;

    return beijing_time < expiry_ms ? 1 : 0;
}

static char* extract_url_host(const char *url, char *host, int host_size) {
    const char *start = strstr(url, "://");
    if (!start) return NULL;
    start += 3;
    const char *end = strchr(start, '/');
    int len = end ? (int)(end - start) : (int)strlen(start);
    if (len >= host_size) len = host_size - 1;
    strncpy(host, start, len);
    host[len] = '\0';
    return (char *)(end ? end : start + len);
}

char* fetch_validation_url(const char *url, const char *device_id) {
    char host[256];
    const char *path = extract_url_host(url, host, sizeof(host));
    if (!path || path[0] == '\0') path = "/";

    char *resp = NULL;
    int len = http_get_full(host, path, &resp);
    if (len <= 0 || !resp) return NULL;

    const char *body = strstr(resp, "\r\n\r\n");
    if (!body) body = strstr(resp, "\n\n");
    if (!body) { free(resp); return NULL; }
    body += body[0] == '\r' ? 4 : 2;

    char pattern[128];
    snprintf(pattern, sizeof(pattern), "%s-", device_id);
    const char *found = strstr(body, pattern);
    if (!found) { free(resp); return NULL; }

    const char *dash2 = strchr(found + strlen(pattern), '-');
    if (dash2) {
        found = strchr(dash2 + 1, '-');
    }

    const char *start = strstr(body, pattern);
    const char *end = start + strlen(pattern) + 12;
    int result_len = (int)(end - start);

    char *result = malloc(result_len + 1);
    strncpy(result, start, result_len);
    result[result_len] = '\0';

    free(resp);
    return result;
}
