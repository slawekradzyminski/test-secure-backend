import React, { useState } from 'react'
import { Input } from "./common/Input";
import { getHandleChange } from "./util/change";
import { PrimaryButton } from "./common/PrimaryButton";
import { createQr } from '../_actions/qr.actions';
import { useAppDispatch } from '../_helpers/store';
import { useSelector } from 'react-redux';
import { RootState } from '../_reducers';
import { CreateQrDto } from '../_services/qr.service';

function QrComponent() {
    const dispatch = useAppDispatch();
    const [text, setText] = useState('')
    const [submitted, setSubmitted] = useState(false);
    const qrCodeUrl = useSelector((state: RootState) => state.qr.url);

    const generateQr = (e) => {
        e.preventDefault();
        setSubmitted(true)
        const createQrDto: CreateQrDto = { text }
        dispatch(createQr({ createQrDto }));
    };

    return (
        <div className="col-lg-8 offset-lg-2">
            <h2>Generate QR Code</h2>
            <form name="form" onSubmit={generateQr}>
                <Input name="text" value={text} submitted={submitted}
                    handleChange={getHandleChange(setText)} />
                <div className="form-group">
                    <PrimaryButton text="Generate QR Code" isLoading={false} />
                </div>
            </form>
            {qrCodeUrl && <img src={qrCodeUrl} alt="Generated QR Code" />}
        </div>
    );
}

export default QrComponent