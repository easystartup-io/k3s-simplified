# Detect OS and Architecture
OS=$(uname -s)
ARCH=$(uname -m)

echo "Detected OS: $OS, Architecture: $ARCH"

if [ "$OS" = "Linux" ] && [ "$ARCH" = "aarch64" ]; then
    curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/arm64/kubectl"
elif [ "$OS" = "Linux" ] && [ ! "$ARCH" = "aarch64" ]; then
    curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
else
    echo "Unsupported OS or Architecture"
    exit 1
fi

sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# Add the kubeconfig file to end of /etc/environment so that kubectl will directly pick up that file
# https://github.com/kubernetes/kubernetes/issues/7339

sudo echo -e "{{ kubeconfig_path_global_env }}" >> /etc/environment