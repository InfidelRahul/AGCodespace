#define _GNU_SOURCE
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>
#include <pty.h>
#include <stdio.h>

static int master_fd = -1;
static pid_t child_pid = -1;

JNIEXPORT jint JNICALL Java_app_agcodespace_runtime_NativePty_start(JNIEnv *env, jobject thiz, jobjectArray argv, jstring cwd, jint cols, jint rows) {
    (void)thiz;
    if (master_fd >= 0 || child_pid > 0) return -EBUSY;
    jsize argc = (*env)->GetArrayLength(env, argv);
    if (argc < 1) return -EINVAL;
    char **args = calloc((size_t)argc + 1, sizeof(char *));
    if (!args) return -ENOMEM;
    for (jsize i = 0; i < argc; ++i) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, argv, i);
        const char *p = (*env)->GetStringUTFChars(env, s, NULL);
        args[i] = strdup(p ? p : "");
        if (p) (*env)->ReleaseStringUTFChars(env, s, p);
        (*env)->DeleteLocalRef(env, s);
        if (!args[i]) { for (jsize j = 0; j < i; ++j) free(args[j]); free(args); return -ENOMEM; }
    }
    const char *dir = cwd ? (*env)->GetStringUTFChars(env, cwd, NULL) : NULL;
    struct winsize ws = { .ws_row = (unsigned short)(rows > 0 ? rows : 24), .ws_col = (unsigned short)(cols > 0 ? cols : 80), .ws_xpixel = 0, .ws_ypixel = 0 };
    int mfd = -1;
    pid_t pid = forkpty(&mfd, NULL, NULL, &ws);
    if (pid == 0) {
        if (dir && chdir(dir) != 0) chdir("/");
        setenv("TERM", "xterm-256color", 1);
        setenv("COLORTERM", "truecolor", 1);
        execvp(args[0], args);
        dprintf(STDERR_FILENO, "AGCodespace: exec %s failed: %s\r\n", args[0], strerror(errno));
        _exit(127);
    }
    if (dir) (*env)->ReleaseStringUTFChars(env, cwd, dir);
    for (jsize i = 0; i < argc; ++i) free(args[i]);
    free(args);
    if (pid < 0) return -errno;
    master_fd = mfd; child_pid = pid;
    int flags = fcntl(master_fd, F_GETFL, 0);
    if (flags >= 0) fcntl(master_fd, F_SETFL, flags | O_NONBLOCK);
    return (jint)pid;
}

JNIEXPORT jbyteArray JNICALL Java_app_agcodespace_runtime_NativePty_read(JNIEnv *env, jobject thiz, jint max_bytes) {
    (void)thiz;
    if (master_fd < 0) return (*env)->NewByteArray(env, 0);
    if (max_bytes < 1) max_bytes = 4096;
    if (max_bytes > 65536) max_bytes = 65536;
    char *buf = malloc((size_t)max_bytes);
    if (!buf) return (*env)->NewByteArray(env, 0);
    ssize_t n = read(master_fd, buf, (size_t)max_bytes);
    if (n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR)) n = 0;
    if (n < 0 && errno == EIO) n = 0;
    jbyteArray out = (*env)->NewByteArray(env, (jsize)(n > 0 ? n : 0));
    if (n > 0) (*env)->SetByteArrayRegion(env, out, 0, (jsize)n, (const jbyte *)buf);
    free(buf);
    return out;
}

JNIEXPORT jint JNICALL Java_app_agcodespace_runtime_NativePty_write(JNIEnv *env, jobject thiz, jbyteArray data) {
    (void)thiz;
    if (master_fd < 0) return -ENOTCONN;
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (!bytes) return -ENOMEM;
    ssize_t n = write(master_fd, bytes, (size_t)len);
    int result = n < 0 ? -errno : (int)n;
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    return result;
}

JNIEXPORT jint JNICALL Java_app_agcodespace_runtime_NativePty_resize(JNIEnv *env, jobject thiz, jint cols, jint rows) {
    (void)env; (void)thiz;
    if (master_fd < 0) return -ENOTCONN;
    struct winsize ws = { .ws_row = (unsigned short)(rows > 0 ? rows : 24), .ws_col = (unsigned short)(cols > 0 ? cols : 80) };
    return ioctl(master_fd, TIOCSWINSZ, &ws) == 0 ? 0 : -errno;
}

JNIEXPORT jint JNICALL Java_app_agcodespace_runtime_NativePty_close(JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    if (master_fd >= 0) { close(master_fd); master_fd = -1; }
    if (child_pid > 0) {
        kill(child_pid, SIGHUP);
        int status = 0;
        pid_t r = waitpid(child_pid, &status, WNOHANG);
        if (r == 0) { kill(child_pid, SIGTERM); waitpid(child_pid, &status, 0); }
        child_pid = -1;
        return status;
    }
    return 0;
}

JNIEXPORT jint JNICALL Java_app_agcodespace_runtime_NativePty_setEnv(JNIEnv *env, jobject thiz, jstring key, jstring value) {
    (void)thiz;
    const char *k = (*env)->GetStringUTFChars(env, key, NULL);
    const char *v = (*env)->GetStringUTFChars(env, value, NULL);
    if (!k || !v) { if (k) (*env)->ReleaseStringUTFChars(env, key, k); if (v) (*env)->ReleaseStringUTFChars(env, value, v); return -ENOMEM; }
    int result = setenv(k, v, 1) == 0 ? 0 : -errno;
    (*env)->ReleaseStringUTFChars(env, key, k); (*env)->ReleaseStringUTFChars(env, value, v);
    return result;
}

JNIEXPORT jboolean JNICALL Java_app_agcodespace_runtime_NativePty_isAlive(JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    if (child_pid <= 0) return JNI_FALSE;
    int status = 0;
    pid_t result = waitpid(child_pid, &status, WNOHANG);
    if (result == 0) return JNI_TRUE;
    if (result == child_pid || (result < 0 && errno == ECHILD)) {
        child_pid = -1;
        if (master_fd >= 0) { close(master_fd); master_fd = -1; }
        return JNI_FALSE;
    }
    return JNI_TRUE;
}
