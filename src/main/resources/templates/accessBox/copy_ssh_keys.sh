touch {{ private_key_path }}
touch {{ public_key_path }}

# https://stackoverflow.com/a/17189399/13939166

tee -a {{ private_key_path }} <<EOF
{{ private_key }}
EOF

tee -a {{ public_key_path }} <<EOF
{{ public_key }}
EOF