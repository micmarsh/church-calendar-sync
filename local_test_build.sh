

 sudo apt remove church-calendar-sync

./build.sh

deb_file=$(ls | grep church-calendar-sync | grep .deb)

sudo apt install ./${deb_file}
