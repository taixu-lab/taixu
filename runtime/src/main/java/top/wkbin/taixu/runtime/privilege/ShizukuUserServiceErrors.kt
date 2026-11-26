package top.wkbin.taixu.runtime.privilege

/** bindUserService 在超时窗口内没有交付 Binder。进程初始化崩溃也会表现为这次超时。 */
internal fun shizukuUserServiceStartTimeoutMessage(timeoutMs: Long): String =
    "Shizuku UserService 启动超时（${timeoutMs}ms）"
