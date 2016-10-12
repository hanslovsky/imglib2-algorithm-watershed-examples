#!/urs/bin/env python

import h5py
import os
import sys
import zwatershed as zw


if __name__ == "__main__":
	import argparse
	parser = argparse.ArgumentParser()
	parser.add_argument('output', nargs='?', type=str,default=None)
	parser.add_argument('--input', '-i', type=str, default=os.path.expanduser('~') + '/Dropbox/misc/excerpt.h5')
	args = parser.parse_args()
	i = args.input
	o = args.output

	with h5py.File(i, 'r') as f:
		data = f['main'].value

	pred = zw.zwatershed(data, [0.0])

	if o:
		with h5py.File(o, 'w') as f:
			f.create_dataset('pred',data=pred[0])
