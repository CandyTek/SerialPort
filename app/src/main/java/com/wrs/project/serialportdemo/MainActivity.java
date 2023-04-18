package com.wrs.project.serialportdemo;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.wrs.project.module.serialport.Device;
import com.wrs.project.module.serialport.SerialPortFinder;
import com.wrs.project.module.serialport.SerialPortManager;
import com.wrs.project.module.serialport.listener.OnSerialPortListener;
import com.wrs.project.serialportdemo.databinding.ActivityMainBinding;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements OnSerialPortListener {
	private ActivityMainBinding binding;
	private Device device;
	private int baudRate; // 波特率
	private int dataBits = 8;//  数据位，值范围：5，6，7，8
	private int parity = 0; // 校验位，值范围：0(不校验)、1(奇校验)、2(偶校验)、3(空校验)
	private int stopBits = 1; // 停止位，值范围：1、2
	private int flowCon = 0; // 流控，值范围：0(不使用流控)、1(硬件流控RTS/CTS)、2(软件流控XON/XOFF)
	private SerialPortManager serialPortManager;
	private Timer timer;

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (null != serialPortManager) {
			serialPortManager.setOnSerialPortListener(null);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
		SerialPortFinder serialPortFinder = new SerialPortFinder();
		List<Device> devices = serialPortFinder.getDevices();
		if (null != devices && devices.size() > 0) {
			device = devices.get(0);
			refreshSerialPort();
		}
		refreshParity();
		refreshFlowCon();
		binding.autoSendCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					startAutoSend();
				} else {
					stopAutoSend();
				}
			}
		});
	}

	/**
	 * 自动发送
	 */
	private void startAutoSend() {
		if (null != timer) {
			timer.cancel();
			timer = null;
		}
		int time = Integer.parseInt(binding.audoSendTimeEditTxt.getText().toString());
		timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				sendData(null);
			}
		}, new Date(), time);
	}

	/**
	 * 停止自动发送
	 */
	private void stopAutoSend() {
		if (null != timer) {
			timer.cancel();
			timer = null;
		}
	}


	public void openSerialPort(View view) {
		if (null != device) {
			if (null == serialPortManager) {
				serialPortManager = new SerialPortManager();
			}
			if (serialPortManager.isOpened()) {
				serialPortManager.closeSerialPort();
			} else {
				serialPortManager.setOnSerialPortListener(this);
				baudRate = Integer.parseInt(binding.baudRateEditTxt.getText().toString());
				serialPortManager.openSerialPort(device.getFile(), baudRate, dataBits, parity, stopBits, flowCon);
			}
		} else {
			showToast("请先选择一个串口！");
		}
	}

	public void sendData(View view) {
		if (null == serialPortManager || !serialPortManager.isOpened()) {
			showToast("请先打开串口！");
			return;
		} else if (TextUtils.isEmpty(binding.sendEditTxt.getText())) {
			showToast("请输入要发送的数据");
			return;
		}
		String str = binding.sendEditTxt.getText().toString();
		byte[] data = null;
		if (binding.sendHexCheckBox.isChecked()) {
			data = ByteUtils.hexStrToByteArray(str);
		} else {
			data = str.getBytes();
		}
		serialPortManager.sendBytes(data);
	}

	public void clearReceiveData(View view) {
		binding.receiveTxtView.setText("");
	}


	public void selectSerialPort(View view) {
		SerialPortFinder serialPortFinder = new SerialPortFinder();
		List<Device> devices = serialPortFinder.getDevices();
		if (null != devices && devices.size() > 0) {
			String[] options = new String[devices.size()];
			for (int i = 0; i < devices.size(); i++) {
				options[i] = devices.get(i).getFile().getAbsolutePath();
			}
			showOptions("选择一个串口", options, new OptionsOnClickListener() {
				@Override
				public void onClick(int index) {
					device = devices.get(index);
					refreshSerialPort();
				}
			});
		} else {
			showToast("没有发现串口！");
		}
	}

	public void selectDataBits(View view) {
		String[] options = {"5", "6", "7", "8"};

		showOptions("选择停止位", options, new OptionsOnClickListener() {
			@Override
			public void onClick(int index) {
				int[] options = {5, 6, 7, 8};
				dataBits = options[index];
				refreshDataBits();
			}
		});
	}

	public void selectParity(View view) {
		String[] options = {"不校验", "奇校验", "偶校验", "空校验"};

		showOptions("选择校验位", options, new OptionsOnClickListener() {
			@Override
			public void onClick(int index) {
				int[] options = {0, 1, 2, 3};
				parity = options[index];
				refreshParity();
			}
		});
	}

	public void selectFlowcon(View view) {
		String[] options = {"不使用", "RTS/CTS", "XON/XOFF"};
		showOptions("选择流控", options, new OptionsOnClickListener() {
			@Override
			public void onClick(int index) {
				int[] options = {0, 1, 2};
				flowCon = options[index];
				refreshFlowCon();
			}
		});
	}

	public void selectStopBits(View view) {
		String[] options = {"1", "2"};

		showOptions("选择停止位", options, new OptionsOnClickListener() {
			@Override
			public void onClick(int index) {
				int[] options = {1, 2};
				stopBits = options[index];
				refreshStopBits();
			}
		});
	}

	private void refreshSerialPort() {
		if (null != device) {
			binding.seaialPortTxtView.setText(device.getFile().getAbsolutePath());
		} else {
			binding.seaialPortTxtView.setText("--");
		}
	}

	private void refreshDataBits() {
		binding.dataBitsTxtView.setText(dataBits + "");
	}

	private void refreshParity() {
		String str = "";
		switch (parity) {
			case 0: {
				str = "不校验";
			}
			break;
			case 1: {
				str = "奇校验";
			}
			break;
			case 2: {
				str = "偶校验";
			}
			break;
			case 3: {
				str = "空校验";
			}
			break;
		}
		binding.parityTxtView.setText(str);
	}

	private void refreshStopBits() {
		binding.stopBitsTxtView.setText(stopBits + "");
	}

	private void refreshFlowCon() {
		String[] options = {"不使用", "RTS/CTS", "XON/XOFF"};
		String str = "";
		switch (flowCon) {
			case 0: {
				str = "不使用";
			}
			break;
			case 1: {
				str = "RTS/CTS";
			}
			break;
			case 2: {
				str = "XON/XOFF";
			}
			break;
		}
		binding.flowConTxtView.setText(str);
	}


	private void showOptions(String title, String[] options, OptionsOnClickListener listener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Holo_Light_Dialog);
		//builder.setIcon(R.drawable.ic_launcher);
		builder.setTitle(title);
		//    设置一个下拉的列表选择项
		builder.setItems(options, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (null != listener) {
					listener.onClick(which);
				}
			}
		});
		builder.show();

	}

	private void showToast(String msg) {
		Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
		toast.show();
	}

	@Override
	public void onSuccess(File device) {
		showToast("串口打开成功！！");
	}

	@Override
	public void onFail(File device, Status status) {
		binding.openSerialPortBtn.setChecked(false);
		showToast("串口打开失败！！");
	}

	@Override
	public void onClose(File device) {
		showToast("串口已经关闭！！");
	}

	@Override
	public void onDataReceived(byte[] bytes) {
		String str = null;
		if (binding.sendHexCheckBox.isChecked()) {
			str = ByteUtils.hexToStr(bytes);
		} else {
			str = new String(bytes);
		}
		Log.i("MainActivyty", "收到串口数据:" + str);
		refreshReceive(str);
	}

	@Override
	public void onDataSent(byte[] bytes) {

	}
	Handler mainHandler = new Handler(Looper.getMainLooper());
	private synchronized void refreshReceive(String str) {
		String curStr = binding.receiveTxtView.getText().toString();
		if (!TextUtils.isEmpty(curStr)) {
			str = curStr + "\n" + str;
		}
		String finalStr = str;
		mainHandler.post(new Runnable() {
			@Override
			public void run() {
				binding.receiveTxtView.setText(finalStr);
			}
		});
		//        runOnUiThread(new Runnable() {
		//            @Override
		//            public void run() {
		//                binding.receiveTxtView.setText(finalStr);
		//            }
		//        });

	}

	interface OptionsOnClickListener {
		void onClick(int index);
	}
}
