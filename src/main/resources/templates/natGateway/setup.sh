apt update && apt upgrade -y
apt install ifupdown

# runtime setup (not persistent)
echo 1 > /proc/sys/net/ipv4/ip_forward
iptables -t nat -A POSTROUTING -s '{{ private_network_subnet }}' -o eth0 -j MASQUERADE
# ----

# making it persistent

touch /etc/network/interfaces

tee -a /etc/network/interfaces <<EOF
auto eth0
iface eth0 inet dhcp
    post-up echo 1 > /proc/sys/net/ipv4/ip_forward
    post-up iptables -t nat -A POSTROUTING -s '{{ private_network_subnet }}' -o eth0 -j MASQUERADE
EOF

# ----