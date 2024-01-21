touch {{ cluster_config_path }}

# https://stackoverflow.com/a/17189399/13939166

tee -a {{ cluster_config_path }} <<EOF
{{ cluster_config }}
EOF
