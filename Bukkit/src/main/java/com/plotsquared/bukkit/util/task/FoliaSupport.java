/*
 * PlotSquared, a land and world management plugin for Minecraft.
 * Copyright (C) IntellectualSites <https://intellectualsites.com>
 * Copyright (C) IntellectualSites team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.plotsquared.bukkit.util.task;

import com.plotsquared.core.util.task.PlotSquaredTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Reflection-based Folia compatibility layer. This keeps the plugin on a single
 * Paper API target while still using Folia schedulers when available at runtime.
 */
public final class FoliaSupport {

    private static volatile boolean initialized;
    private static boolean folia;

    private static Method serverGetGlobalRegionScheduler;
    private static Method serverGetRegionScheduler;
    private static Method serverGetAsyncScheduler;

    private static Method globalRun;
    private static Method globalRunDelayed;
    private static Method globalRunAtFixedRate;
    private static Method globalExecute;

    private static Method regionExecute;

    private static Method asyncRunNow;
    private static Method asyncRunDelayed;
    private static Method asyncRunAtFixedRate;

    private FoliaSupport() {
    }

    public static boolean isFolia() {
        ensureInitialized();
        return folia;
    }

    public static PlotSquaredTask run(final @NonNull Plugin plugin, final @NonNull Runnable runnable) {
        ensureInitialized();
        if (!folia) {
            final BukkitPlotSquaredTask task = new BukkitPlotSquaredTask(runnable);
            task.runTask(plugin);
            return task;
        }
        final FoliaTask task = new FoliaTask(runnable);
        final Object scheduler = invoke(serverGetGlobalRegionScheduler, Bukkit.getServer());
        if (globalExecute != null) {
            invoke(globalExecute, scheduler, plugin, (Runnable) task);
        } else {
            task.setHandle(invoke(globalRun, scheduler, plugin, consumer(task)));
        }
        return task;
    }

    public static void runLater(final @NonNull Plugin plugin, final @NonNull Runnable runnable, final long delayTicks) {
        ensureInitialized();
        if (!folia) {
            new BukkitPlotSquaredTask(runnable).runTaskLater(plugin, delayTicks);
            return;
        }
        invoke(globalRunDelayed, invoke(serverGetGlobalRegionScheduler, Bukkit.getServer()), plugin, consumer(new FoliaTask(runnable)),
                delayTicks);
    }

    public static PlotSquaredTask runAtFixedRate(
            final @NonNull Plugin plugin,
            final @NonNull Runnable runnable,
            final long delayTicks,
            final long periodTicks
    ) {
        ensureInitialized();
        if (!folia) {
            final BukkitPlotSquaredTask task = new BukkitPlotSquaredTask(runnable);
            task.runTaskTimer(plugin, delayTicks, periodTicks);
            return task;
        }
        final FoliaTask task = new FoliaTask(runnable);
        task.setHandle(invoke(
                globalRunAtFixedRate,
                invoke(serverGetGlobalRegionScheduler, Bukkit.getServer()),
                plugin,
                consumer(task),
                delayTicks,
                periodTicks
        ));
        return task;
    }

    public static void runAsync(final @NonNull Plugin plugin, final @NonNull Runnable runnable) {
        ensureInitialized();
        if (!folia) {
            new BukkitPlotSquaredTask(runnable).runTaskAsynchronously(plugin);
            return;
        }
        invoke(asyncRunNow, invoke(serverGetAsyncScheduler, Bukkit.getServer()), plugin, consumer(new FoliaTask(runnable)));
    }

    public static void runLaterAsync(final @NonNull Plugin plugin, final @NonNull Runnable runnable, final long delayTicks) {
        ensureInitialized();
        if (!folia) {
            new BukkitPlotSquaredTask(runnable).runTaskLaterAsynchronously(plugin, delayTicks);
            return;
        }
        invoke(
                asyncRunDelayed,
                invoke(serverGetAsyncScheduler, Bukkit.getServer()),
                plugin,
                consumer(new FoliaTask(runnable)),
                ticksToMillis(delayTicks),
                TimeUnit.MILLISECONDS
        );
    }

    public static PlotSquaredTask runAtFixedRateAsync(
            final @NonNull Plugin plugin,
            final @NonNull Runnable runnable,
            final long delayTicks,
            final long periodTicks
    ) {
        ensureInitialized();
        if (!folia) {
            final BukkitPlotSquaredTask task = new BukkitPlotSquaredTask(runnable);
            task.runTaskTimerAsynchronously(plugin, delayTicks, periodTicks);
            return task;
        }
        final FoliaTask task = new FoliaTask(runnable);
        task.setHandle(invoke(
                asyncRunAtFixedRate,
                invoke(serverGetAsyncScheduler, Bukkit.getServer()),
                plugin,
                consumer(task),
                ticksToMillis(delayTicks),
                ticksToMillis(periodTicks),
                TimeUnit.MILLISECONDS
        ));
        return task;
    }

    public static <T> Future<T> callSync(final @NonNull Plugin plugin, final @NonNull Callable<T> method) {
        ensureInitialized();
        if (!folia) {
            return Bukkit.getScheduler().callSyncMethod(plugin, method);
        }
        final CompletableFuture<T> future = new CompletableFuture<>();
        run(plugin, () -> {
            try {
                future.complete(method.call());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public static void runAtLocation(final @NonNull Plugin plugin, final @NonNull Location location, final @NonNull Runnable runnable) {
        runAtLocation(plugin, Objects.requireNonNull(location.getWorld()), location.getBlockX(), location.getBlockZ(), runnable);
    }

    public static void runAtLocation(
            final @NonNull Plugin plugin,
            final @NonNull World world,
            final int x,
            final int z,
            final @NonNull Runnable runnable
    ) {
        ensureInitialized();
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, runnable);
            return;
        }
        invoke(regionExecute, invoke(serverGetRegionScheduler, Bukkit.getServer()), plugin, world, x, z, runnable);
    }

    private static Consumer<Object> consumer(final FoliaTask task) {
        return ignored -> task.run();
    }

    private static long ticksToMillis(final long ticks) {
        return Math.max(0L, ticks) * 50L;
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (FoliaSupport.class) {
            if (initialized) {
                return;
            }
            try {
                final Class<?> serverClass = Bukkit.getServer().getClass();
                serverGetGlobalRegionScheduler = serverClass.getMethod("getGlobalRegionScheduler");
                serverGetRegionScheduler = serverClass.getMethod("getRegionScheduler");
                serverGetAsyncScheduler = serverClass.getMethod("getAsyncScheduler");

                final Object globalScheduler = serverGetGlobalRegionScheduler.invoke(Bukkit.getServer());
                final Class<?> globalSchedulerClass = globalScheduler.getClass();
                globalRun = globalSchedulerClass.getMethod("run", Plugin.class, Consumer.class);
                globalRunDelayed = globalSchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
                globalRunAtFixedRate =
                        globalSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
                try {
                    globalExecute = globalSchedulerClass.getMethod("execute", Plugin.class, Runnable.class);
                } catch (NoSuchMethodException ignored) {
                    globalExecute = null;
                }

                final Object regionScheduler = serverGetRegionScheduler.invoke(Bukkit.getServer());
                regionExecute = regionScheduler.getClass().getMethod(
                        "execute",
                        Plugin.class,
                        World.class,
                        int.class,
                        int.class,
                        Runnable.class
                );

                final Object asyncScheduler = serverGetAsyncScheduler.invoke(Bukkit.getServer());
                final Class<?> asyncSchedulerClass = asyncScheduler.getClass();
                asyncRunNow = asyncSchedulerClass.getMethod("runNow", Plugin.class, Consumer.class);
                asyncRunDelayed =
                        asyncSchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);
                asyncRunAtFixedRate = asyncSchedulerClass.getMethod(
                        "runAtFixedRate",
                        Plugin.class,
                        Consumer.class,
                        long.class,
                        long.class,
                        TimeUnit.class
                );

                folia = true;
            } catch (Throwable ignored) {
                folia = false;
            }
            initialized = true;
        }
    }

    private static Object invoke(final Method method, final Object target, final Object... args) {
        try {
            final Method resolvedMethod = Objects.requireNonNull(method, "method");
            if (!resolvedMethod.canAccess(target)) {
                resolvedMethod.setAccessible(true);
            }
            return resolvedMethod.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke Folia scheduler method", e);
        }
    }

    private static final class FoliaTask implements PlotSquaredTask {

        private final Runnable runnable;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Object handle;
        private volatile Method cancelMethod;

        private FoliaTask(final Runnable runnable) {
            this.runnable = runnable;
        }

        private void setHandle(final Object handle) {
            this.handle = handle;
            if (handle != null) {
                try {
                    this.cancelMethod = handle.getClass().getMethod("cancel");
                    this.cancelMethod.setAccessible(true);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Failed to resolve Folia task cancel method", e);
                }
            }
        }

        @Override
        public void runTask() {
            this.runnable.run();
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled.get();
        }

        @Override
        public void cancel() {
            if (!this.cancelled.compareAndSet(false, true)) {
                return;
            }
            if (this.handle != null && this.cancelMethod != null) {
                invoke(this.cancelMethod, this.handle);
            }
        }
    }
}
