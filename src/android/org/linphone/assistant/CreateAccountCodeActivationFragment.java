package org.linphone.assistant;
/*
CreateAccountCodeActivationFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListener;
import org.linphone.core.Factory;

import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class CreateAccountCodeActivationFragment extends Fragment implements AccountCreatorListener {
	private String username, phone, dialcode;
	private TextView title, phonenumber;
	private EditText code;
	private boolean recoverAccount = false, linkAccount = false;
	private int code_length, accountNumber;
	private ImageView back;
	private Button checkAccount;
	private AccountCreator accountCreator;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.assistant_account_creation_code_activation, container, false);

		username = getArguments().getString("Username");
		phone = getArguments().getString("Phone");
		dialcode = getArguments().getString("Dialcode");
		recoverAccount = getArguments().getBoolean("RecoverAccount");
		linkAccount = getArguments().getBoolean("LinkAccount");
		accountNumber = getArguments().getInt("AccountNumber");

		code_length = LinphonePreferences.instance().getCodeLength();
		accountCreator = LinphoneManager.getLc().createAccountCreator(LinphonePreferences.instance().getXmlrpcUrl());
		accountCreator.setListener(this);
		accountCreator.setUsername(username);
		accountCreator.setPhoneNumber(phone, dialcode);

		back = (ImageView) view.findViewById(R.id.back);
		if (back != null)
			back.setVisibility(Button.INVISIBLE);

		title = (TextView) view.findViewById(R.id.title_account_activation);
		if (linkAccount) {
			title.setText(getString(R.string.assistant_link_account));
		} else if (recoverAccount) {
			title.setText(getString(R.string.assistant_linphone_account));
		}

		phonenumber = (TextView) view.findViewById(R.id.send_phone_number);
		phonenumber.setText(accountCreator.getPhoneNumber());

		code = (EditText) view.findViewById(R.id.assistant_code);
		code.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				if(s.length() == code_length){
					checkAccount.setEnabled(true);
				} else {
					checkAccount.setEnabled(false);
				}
			}
		});

		checkAccount = (Button) view.findViewById(R.id.assistant_check);
		checkAccount.setEnabled(false);
		checkAccount.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				checkAccount.setEnabled(false);
				accountCreator.setActivationCode(code.getText().toString());
				if(linkAccount){
					linkAccount();
				} else {
					activateAccount();
				}
			}
		});


		return view;
	}

	private void linkAccount(){
		accountCreator.setUsername(LinphonePreferences.instance().getAccountUsername(accountNumber));
		accountCreator.setHa1(LinphonePreferences.instance().getAccountHa1(accountNumber));
		accountCreator.activateAlias();
	}

	private void activateAccount() {
		if(accountCreator.getUsername() == null){
			accountCreator.setUsername(accountCreator.getPhoneNumber());
		}
		accountCreator.activateAccount();
	}

	@Override
	public void onIsAccountExist(AccountCreator accountCreator, AccountCreator.Status status, String resp) {
	}

	@Override
	public void onCreateAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {
	}

	@Override
	public void onActivateAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {
		if (AssistantActivity.instance() == null) {
			return;
		}
		if (status.equals(AccountCreator.Status.AccountActivated)) {
			checkAccount.setEnabled(true);
			if (accountCreator.getUsername() != null) {
				AssistantActivity.instance().linphoneLogIn(accountCreator);
				if(!recoverAccount){
					AssistantActivity.instance().isAccountVerified(accountCreator.getUsername());
				} else {
					AssistantActivity.instance().success();
				}
			} else {
				AssistantActivity.instance().linphoneLogIn(accountCreator);
				if(!recoverAccount) {
					AssistantActivity.instance().isAccountVerified(accountCreator.getPhoneNumber());
				} else {
					AssistantActivity.instance().success();
				}
			}
		} else if (status.equals(AccountCreator.Status.RequestFailed)) {
			Toast.makeText(getActivity(), getString(R.string.wizard_server_unavailable), Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(getActivity(), getString(R.string.assistant_error_confirmation_code), Toast.LENGTH_LONG).show();
			AssistantActivity.instance().displayAssistantLinphoneLogin(phone, dialcode);
		}
	}

	@Override
	public void onLinkAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {

	}

	@Override
	public void onActivateAlias(AccountCreator accountCreator, AccountCreator.Status status, String resp) {
		if (AssistantActivity.instance() == null) {
			return;
		}
		if(status.equals(AccountCreator.Status.AccountActivated)){
			LinphonePreferences.instance().setPrefix(accountNumber, org.linphone.core.Utils.getPrefixFromE164(accountCreator.getPhoneNumber()));
			LinphonePreferences.instance().setLinkPopupTime("");
			AssistantActivity.instance().hideKeyboard();
			AssistantActivity.instance().success();
		}
	}

	@Override
	public void onIsAccountActivated(AccountCreator accountCreator, AccountCreator.Status status, String resp) {

	}

	@Override
	public void onRecoverAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {
	}

	@Override
	public void onIsAccountLinked(AccountCreator accountCreator, AccountCreator.Status status, String resp) {

	}

	@Override
	public void onIsAliasUsed(AccountCreator accountCreator, AccountCreator.Status status, String resp) {

	}

	@Override
	public void onUpdateAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {

	}

	@Override
	public void onGetConfirmationKey(AccountCreator ac, AccountCreator.Status status, String key) {

	}

	@Override
	public void onDeleteAccount(AccountCreator ac, AccountCreator.Status status, String resp) {

	}
}
