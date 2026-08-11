#!/bin/sh

# doesn't work in fakeroot?
# lein jdeb

tmp_dir=DELETE_ME_temp_for_build

desktop_file_dir=${tmp_dir}/usr/share/applications
bin_file_dir=${tmp_dir}/usr/bin

package_and_binary_name=church-calendar-sync
desktop_file=${package_and_binary_name}.desktop

deb_file=$(ls | grep $package_and_binary_name | grep .deb)

dpkg-deb -R $deb_file $tmp_dir

mkdir -p $desktop_file_dir
mkdir -p $bin_file_dir
cp $desktop_file  ${desktop_file_dir}/${desktop_file}
cp $package_and_binary_name ${bin_file_dir}/${package_and_binary_name}

dpkg-deb -b $tmp_dir $deb_file

rm -r $tmp_dir