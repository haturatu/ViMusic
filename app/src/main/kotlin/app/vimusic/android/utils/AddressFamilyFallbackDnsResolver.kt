package app.vimusic.android.utils

import dev.kathttp3.DnsResolver
import dev.kathttp3.ResolvedAddress
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/** Resolves one family at a time and retries an unreachable route on the opposite family. */
internal class AddressFamilyFallbackDnsResolver : DnsResolver {
    enum class AddressFamily { Ipv4, Ipv6 }

    private data class HostState(
        var preferredFamily: AddressFamily,
        val addresses: MutableMap<AddressFamily, List<String>>,
    )

    private val states = mutableMapOf<String, HostState>()

    override fun resolve(host: String, port: Int): List<ResolvedAddress> {
        val platformAddresses = InetAddress.getAllByName(host).toList()
        return synchronized(states) {
            val currentAddresses = platformAddresses.byFamily()
            val state = states[host]?.also { cached ->
                currentAddresses.forEach { (family, addresses) ->
                    if (addresses.isNotEmpty()) cached.addresses[family] = addresses
                }
            } ?: HostState(
                preferredFamily = platformAddresses.firstNotNullOfOrNull { it.addressFamily() }
                    ?: return@synchronized emptyList(),
                addresses = currentAddresses.toMutableMap(),
            ).also { states[host] = it }

            state.addresses[state.preferredFamily].orEmpty().map { ip ->
                ResolvedAddress(ip, port)
            }
        }
    }

    fun switchFamily(host: String, failedFamily: AddressFamily?): Boolean {
        val platformAddresses = InetAddress.getAllByName(host).toList()
        return synchronized(states) {
            val currentAddresses = platformAddresses.byFamily()
            val state = states[host]?.also { cached ->
                currentAddresses.forEach { (family, addresses) ->
                    if (addresses.isNotEmpty()) cached.addresses[family] = addresses
                }
            } ?: HostState(
                preferredFamily = platformAddresses.firstNotNullOfOrNull { it.addressFamily() }
                    ?: return@synchronized false,
                addresses = currentAddresses.toMutableMap(),
            ).also { states[host] = it }
            val oppositeFamily = (failedFamily ?: state.preferredFamily).opposite()
            if (state.addresses[oppositeFamily].isNullOrEmpty()) {
                return@synchronized false
            }
            state.preferredFamily = oppositeFamily
            true
        }
    }

    fun lookup(host: String): List<InetAddress> {
        val addresses = resolve(host, HTTPS_PORT).map { InetAddress.getByName(it.ip) }
        if (addresses.isEmpty()) throw UnknownHostException("No addresses for $host")
        return addresses
    }

    private fun List<InetAddress>.byFamily(): Map<AddressFamily, List<String>> =
        mapNotNull { address ->
            val family = address.addressFamily() ?: return@mapNotNull null
            val ip = address.hostAddress ?: return@mapNotNull null
            family to ip
        }.groupBy({ it.first }, { it.second })

    private fun AddressFamily.opposite(): AddressFamily = when (this) {
        AddressFamily.Ipv4 -> AddressFamily.Ipv6
        AddressFamily.Ipv6 -> AddressFamily.Ipv4
    }

    private fun InetAddress.addressFamily(): AddressFamily? = when (this) {
        is Inet4Address -> AddressFamily.Ipv4
        is Inet6Address -> AddressFamily.Ipv6
        else -> null
    }

    private companion object {
        const val HTTPS_PORT = 443
    }
}
