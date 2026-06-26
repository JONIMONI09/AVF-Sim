#include <jni.h>
#include <string>
#include <cstdlib>
#include <unistd.h>
#include <android/log.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <vector>

#define TAG "AVFSimulator-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
Java_com_example_MainActivity_executeNativeCommand(
        JNIEnv* env,
        jobject /* this */,
        jstring command) {
    const char *cmd = env->GetStringUTFChars(command, nullptr);
    LOGI("Native execute: %s", cmd);
    
    int result = system(cmd);
    
    env->ReleaseStringUTFChars(command, cmd);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_MainActivity_forkAndExec(
        JNIEnv* env,
        jobject /* this */,
        jstring command) {
    const char *cmd = env->GetStringUTFChars(command, nullptr);
    LOGI("Fork & Exec: %s", cmd);
    
    pid_t pid = fork();
    if (pid == 0) {
        // Child process
        
        // Optimize: Close all inherited file descriptors except stdio
        for (int i = 3; i < 1024; ++i) {
            close(i);
        }

        // Set up basic environment for Podroid-like speed
        setenv("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin:/system/xbin", 1);
        setenv("TERM", "xterm-256color", 1);
        
        execl("/system/bin/sh", "sh", "-c", cmd, nullptr);
        
        // If execl fails
        LOGE("Failed to execute /system/bin/sh: %s", strerror(errno));
        _exit(127);
    } else if (pid > 0) {
        // Parent process
        int status;
        if (waitpid(pid, &status, 0) == -1) {
            LOGE("waitpid failed: %s", strerror(errno));
            env->ReleaseStringUTFChars(command, cmd);
            return -1;
        }
        
        int exit_code = -1;
        if (WIFEXITED(status)) {
            exit_code = WEXITSTATUS(status);
            LOGI("Child process exited with code: %d", exit_code);
        } else if (WIFSIGNALED(status)) {
            LOGI("Child process killed by signal: %d", WTERMSIG(status));
        }
        
        env->ReleaseStringUTFChars(command, cmd);
        return exit_code;
    }
    
    LOGE("fork failed: %s", strerror(errno));
    env->ReleaseStringUTFChars(command, cmd);
    return -1;
}
