import React, { useContext, useState } from 'react'
import { useSelector } from "react-redux";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Input } from "./common/Input";
import { getHandleChange } from "./util/change";
import { DisabledInput } from "./common/DisabledInput";
import { PrimaryButton } from "./common/PrimaryButton";
import { update } from '../_actions/user.actions';
import { ToastContext } from '../context/ToastContext';
import { useAppDispatch } from '../_helpers/store';
import { RootState } from '../_reducers';

function EditUserComponent() {
    const location = useLocation();
    const navigate = useNavigate();
    const dispatch = useAppDispatch();
    const user = location.state.user;
    const [username, setUsername] = useState(user.username)
    const [firstName, setFirstName] = useState(user.firstName)
    const [lastName, setLastName] = useState(user.lastName)
    const [email, setEmail] = useState(user.email)
    const [roles, setRoles] = useState(user.roles)
    const loading = useSelector((state: RootState) => state.edituser.loading);
    const [submitted, setSubmitted] = useState(false);
    const setToast = useContext(ToastContext);

    const saveUser = (e) => {
        e.preventDefault();
        setSubmitted(true)
        const user = { firstName, lastName, username, email, roles }
        dispatch(update({ user, setToast, navigate }));
    };

    if (username === null) {
        return (
            <div className="col-lg-8 offset-lg-2">
                <h2>Something is no yes...</h2>
                <Link to="/" className="btn btn-link">Go back</Link>
            </div>
        );
    }

    if (loading) {
        return <div>Loading...</div>;
    }

    return (
        <div className="col-lg-8 offset-lg-2">
            <h2>Edit user</h2>
            {username &&
                <form name="form" onSubmit={saveUser}>
                    <Input name="firstName" value={firstName} submitted={submitted}
                        handleChange={getHandleChange(setFirstName)} />
                    <Input name="lastName" value={lastName} submitted={submitted}
                        handleChange={getHandleChange(setLastName)} />
                    <Input name="email" value={email} submitted={submitted}
                        handleChange={getHandleChange(setEmail)} />
                    <DisabledInput name="username" value={username} />
                    <DisabledInput name="roles" value={roles.join(',')} />
                    <div className="form-group">
                        <PrimaryButton text="Edit User" isLoading={loading} />
                        <Link to="/" className="btn btn-link">Cancel</Link>
                    </div>
                </form>
            }
        </div>
    );
}

export { EditUserComponent };