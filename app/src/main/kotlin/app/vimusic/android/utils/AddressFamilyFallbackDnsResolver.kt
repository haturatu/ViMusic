package app.vimusic.android.utils

import dev.kathttp3.DnsResolver
import dev.kathttp3.ResolvedAddress
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/** Resolves one address family at a time and allows an unreachable route to flip the family. */
internal class AddressFamilyFallbackDnsResolver : DnsResolver {
    private enum class AddressFamily { Ipv4, Ipv6 }

    private val preferredFamilies = ConcurrentHashMap<String, AddressFamily>()

    override fun resolve(host: String, port: Int): List<ResolvedAddress> {
        val addresses = InetAddress.getAllByName(host).toList()
        val availableFamilies = addresses.mapNotNull { it.addressFamily() }.toSet()
        val preferred = preferredFamilies.compute(host) { _, current ->
            current?.takeIf(availableFamilies::contains)
                ?: addresses.firstNotNullOfOrNull { it.addressFamily() }
        } ?: return emptyList()

        return addresses
            .filter { it.addressFamily() == preferred }
            .mapNotNull { address ->
                address.hostAddress?.let { ResolvedAddress(it, port) }
            }
    }

    fun switchFamily(host: String): Boolean {
        val addresses = InetAddress.getAllByName(host).toList()
        val availableFamilies = addresses.mapNotNull { it.addressFamily() }.toSet()
        if (availableFamilies.size < 2) return false

        var switched = false
        preferredFamilies.compute(host) { _, current ->
            val active = current ?: addresses.firstNotNullOfOrNull { it.addressFamily() }
            val opposite = when (active) {
                AddressFamily.Ipv4 -> AddressFamily.Ipv6
                AddressFamily.Ipv6 -> AddressFamily.Ipv4
                null -> null
            }
            opposite?.takeIf(availableFamilies::contains)?.also { switched = true } ?: active
        }
        return switched
    }

    private fun InetAddress.addressFamily(): AddressFamily? = when (this) {
        is Inet4Address -> AddressFamily.Ipv4
        is Inet6Address -> AddressFamily.Ipv6
        else -> null
    }
}
