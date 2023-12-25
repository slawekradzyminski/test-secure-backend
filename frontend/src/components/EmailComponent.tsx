import React, { useContext, useState } from 'react'
import { Link, useLocation } from "react-router-dom";
import { Input } from "./common/Input";
import { getHandleChange } from "./util/change";
import { DisabledInput } from "./common/DisabledInput";
import { PrimaryButton } from "./common/PrimaryButton";
import { Textarea } from "./common/Textarea";
import { Email } from '../types';
import { handleEmail } from '../_actions/user.actions';
import { useAppDispatch } from '../_helpers/store';
import { ToastContext } from '../context/ToastContext';

function EmailComponent() {
    const location = useLocation()
    const dispatch = useAppDispatch();
    const user = location.state.user;
    const [to, setTo] = useState(user.email)
    const [subject, setSubject] = useState('')
    const [message, setMessage] = useState('')
    const [submitted, setSubmitted] = useState(false);
    const setToast = useContext(ToastContext);

    const sendEmail = (e) => {
        e.preventDefault();
        setSubmitted(true)
        const email: Email = { to, subject, message }
        dispatch(handleEmail({ email, setToast }));
    };

    if (to === undefined) {
        return (
            <div className="col-lg-8 offset-lg-2">
                <h2>Something is no yes...</h2>
                <Link to="/" className="btn btn-link">Go back</Link>
            </div>
        );
    }

    return (
        <div className="col-lg-8 offset-lg-2">
            <h2>Email user</h2>
            {to &&
                <form name="form" onSubmit={sendEmail}>
                    <DisabledInput name="email" value={to} />
                    <Input name="subject" value={subject} submitted={submitted}
                        handleChange={getHandleChange(setSubject)} />
                    <Textarea name="message" value={message} submitted={submitted}
                        handleChange={getHandleChange(setMessage)} />
                    <div className="form-group">
                        <PrimaryButton text="Send email" isLoading={false} />
                        <Link to="/" className="btn btn-link">Cancel</Link>
                    </div>
                </form>
            }
        </div>
    );
}

export { EmailComponent };