# Cài đặt

sudo dpkg -i /path/helix-browser_3.0.0_amd64.deb
sudo apt-get install -f # cài dependencies nếu thiếu

# Chạy

helix-browser

# Gỡ cài đặt

sudo dpkg -r helix-browser && sudo dpkg -i /home/thien/Projects/helix_browser/linux/helix-browser_3.0.0_amd64.deb

# Rebuild và cài lại

cd /home/thien/Projects/helix_browser/linux && sudo rm -rf build && bash build-deb.sh && sudo dpkg -r helix-browser && sudo dpkg -i helix-browser_3.0.0_amd64.deb && helix-browser
