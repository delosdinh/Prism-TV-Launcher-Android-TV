package com.prismtv.launcher.system

import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * A tiny adb client that talks to this TV's own adb (127.0.0.1:5555), the way a computer or ATV
 * Connect would. Used once, with your OK, to start Shizuku — which needs the shell user to launch.
 *
 * The TV shows Android's "Allow debugging?" prompt the first time (tick "Always allow" so Shizuku
 * can be started again after a restart without asking). Wireless debugging's TLS pairing isn't
 * supported: TVs whose adb only listens there need Shizuku started from a computer instead.
 */
object LocalAdb {
    sealed interface Result {
        /** The command ran; its output, and its exit code when it could be read. */
        data class Ok(val output: String, val exit: Int?) : Result

        /** USB debugging (Developer options) is off. */
        data object Off : Result

        /** Nothing listens on the TV's adb port (network debugging is off, or only Wireless debugging is on). */
        data object Unreachable : Result

        /** The TV wants you to approve Prism TV and [allowPrompt] was false (or you said no / didn't answer). */
        data object NotAllowed : Result

        data class Failed(val why: String) : Result
    }

    private const val CNXN = 0x4e584e43
    private const val AUTH = 0x48545541
    private const val OPEN = 0x4e45504f
    private const val OKAY = 0x59414b4f
    private const val CLSE = 0x45534c43
    private const val WRTE = 0x45545257
    private const val STLS = 0x534c5453
    private const val VERSION = 0x01000001
    private const val MAX_DATA = 256 * 1024
    private const val AUTH_TOKEN = 1
    private const val AUTH_SIGNATURE = 2
    private const val AUTH_RSAPUBLICKEY = 3
    private const val ExitMarker = "PRISM_EXIT="

    /** Developer options › USB debugging. */
    fun enabled(ctx: Context): Boolean =
        runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 }.getOrDefault(false)

    /** Developer options themselves are turned on. */
    fun developerOptions(ctx: Context): Boolean =
        runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 }.getOrDefault(false)

    /**
     * Runs [command] as the shell user. With [allowPrompt] the TV may show "Allow debugging?" and this
     * waits up to a minute for an answer; without it, an unknown key just returns [Result.NotAllowed].
     * Blocking: call off the main thread.
     */
    fun shell(ctx: Context, command: String, allowPrompt: Boolean, port: Int = 5555): Result {
        if (!enabled(ctx)) return Result.Off
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress("127.0.0.1", port), 3000)
        } catch (_: Throwable) {
            runCatching { socket.close() }
            return Result.Unreachable
        }
        return try {
            socket.use { s -> session(ctx, s, command, allowPrompt) }
        } catch (_: SocketTimeoutException) {
            Result.Failed("No answer from the TV's adb.")
        } catch (t: Throwable) {
            Result.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun session(ctx: Context, s: Socket, command: String, allowPrompt: Boolean): Result {
        s.tcpNoDelay = true
        s.soTimeout = 10_000
        val input = DataInputStream(BufferedInputStream(s.getInputStream()))
        val out = BufferedOutputStream(s.getOutputStream())
        val keys = keyPair(ctx)
        send(out, CNXN, VERSION, MAX_DATA, "host::prismtv\u0000".toByteArray())
        var sentSignature = false
        var sentKey = false
        connect@ while (true) {
            val m = try {
                read(input)
            } catch (e: SocketTimeoutException) {
                return if (sentKey) Result.NotAllowed else throw e
            }
            when (m.cmd) {
                CNXN -> break@connect
                STLS -> return Result.Failed("This TV's adb wants a Wireless debugging (TLS) connection.")
                AUTH -> if (m.arg0 == AUTH_TOKEN) {
                    when {
                        !sentSignature -> {
                            sentSignature = true
                            send(out, AUTH, AUTH_SIGNATURE, 0, sign(keys.private, m.data))
                        }
                        !sentKey -> {
                            if (!allowPrompt) return Result.NotAllowed
                            sentKey = true
                            s.soTimeout = 60_000 // "Allow debugging?" is on screen
                            send(out, AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyBlob(keys.public as RSAPublicKey))
                        }
                        else -> return Result.NotAllowed
                    }
                }
            }
        }
        s.soTimeout = 30_000
        val localId = 1
        send(out, OPEN, localId, 0, "shell:$command; echo $ExitMarker\$?\u0000".toByteArray())
        val output = ByteArrayOutputStream()
        var remoteId = 0
        stream@ while (true) {
            val m = read(input)
            when (m.cmd) {
                OKAY -> remoteId = m.arg0
                WRTE -> {
                    remoteId = m.arg0
                    output.write(m.data)
                    send(out, OKAY, localId, remoteId)
                }
                CLSE -> {
                    if (remoteId != 0) runCatching { send(out, CLSE, localId, remoteId) }
                    break@stream
                }
            }
        }
        val text = output.toString("UTF-8")
        val exit = text.substringAfterLast(ExitMarker, "").trim().takeWhile { it.isDigit() }.toIntOrNull()
        return Result.Ok(text.substringBeforeLast(ExitMarker).trim(), exit)
    }

    // ------------------------------------------------------------------ wire format

    private class Msg(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private fun send(out: OutputStream, cmd: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) {
        val b = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.size)
            .putInt(data.sumOf { it.toInt() and 0xff }).putInt(cmd.inv()).put(data)
        out.write(b.array())
        out.flush()
    }

    private fun read(input: DataInputStream): Msg {
        val header = ByteArray(24)
        input.readFully(header)
        val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = b.int
        val arg0 = b.int
        val arg1 = b.int
        val length = b.int
        require(length in 0..(1 shl 20)) { "Bad adb message" }
        val data = ByteArray(length)
        input.readFully(data)
        return Msg(cmd, arg0, arg1, data)
    }

    // ------------------------------------------------------------------ Prism TV's adb key

    private val Sha1DigestInfo = byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
    )

    /** adbd checks an RSA PKCS#1 v1.5 signature of its 20-byte token, treated as a SHA-1 digest. */
    private fun sign(key: PrivateKey, token: ByteArray): ByteArray = try {
        java.security.Signature.getInstance("NONEwithRSA").run {
            initSign(key)
            update(Sha1DigestInfo)
            update(token)
            sign()
        }
    } catch (_: Throwable) {
        javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
            init(javax.crypto.Cipher.ENCRYPT_MODE, key)
            update(Sha1DigestInfo)
            doFinal(token)
        }
    }

    @Synchronized
    private fun keyPair(ctx: Context): KeyPair {
        val dir = File(ctx.filesDir, "adb").apply { mkdirs() }
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        if (priv.exists() && pub.exists()) {
            runCatching {
                val kf = KeyFactory.getInstance("RSA")
                return KeyPair(
                    kf.generatePublic(X509EncodedKeySpec(pub.readBytes())),
                    kf.generatePrivate(PKCS8EncodedKeySpec(priv.readBytes())),
                )
            }
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        priv.writeBytes(kp.private.encoded)
        pub.writeBytes(kp.public.encoded)
        return kp
    }

    /** Android's adb public key format: a little-endian RSAPublicKey struct, base64, then a name. */
    private fun publicKeyBlob(pub: RSAPublicKey): ByteArray {
        val words = 64 // 2048-bit modulus
        val n = pub.modulus
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = r32.subtract(n.mod(r32).modInverse(r32)).toInt()
        val rr = BigInteger.ONE.shiftLeft(4096).mod(n) // R² mod n, R = 2^2048
        val mask = BigInteger.valueOf(0xffffffffL)
        val b = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(words)
        b.putInt(n0inv)
        for (i in 0 until words) b.putInt(n.shiftRight(32 * i).and(mask).toInt())
        for (i in 0 until words) b.putInt(rr.shiftRight(32 * i).and(mask).toInt())
        b.putInt(pub.publicExponent.toInt())
        return (Base64.encodeToString(b.array(), Base64.NO_WRAP) + " prismtv@tv\u0000").toByteArray()
    }
}
