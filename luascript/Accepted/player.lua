function Player.addHealthExtend(player, amount, sleep, deduction, ac, ds, print)
	local healer
	local ded = 0

	if (player.state == 1) then
		return
	end

	if (player.attacker >= 1073741823) then
		healer = Mob(player.attacker)
	elseif (player.attacker > 0) then
		healer = Player(player.attacker)
	end

	--ded = 1 * string.format("%.2f", player.armor / (player.armor + 400 + 95 * (healer.level + healer.tier^2 + healer.mark^3)))

	-- healer bisa nil: kalau player.attacker masih 0 (belum pernah dilukai
	-- sejak masuk), kedua cabang di atas tidak berjalan. Baris ded di atas
	-- yang dulu memakai healer sudah dikomentari, tetapi baris ini tidak ikut
	-- — jadi memakan makanan sebelum pernah bertarung menggagalkan seluruh
	-- fungsi ini. Penjaga nil, bukan perubahan aturan permainan.
	if (healer ~= nil and healer:hasDuration("blossom")) then
		amount = amount * 2
	end

	if (sleep > 0 and print == 2) then
		amount = amount * player.sleep
	elseif (sleep == 1) then
		amount = amount * player.sleep
		player.sleep = 1
		player:updateState()
	elseif (sleep == 2) then
		amount = amount * player.sleep
	end

	if (deduction == 1) then
		if (player.deduction < 0) then
			amount = 0
		elseif (player.deduction > 0) then
			amount = amount * player.deduction
		end
	end

	if (ac == 1) then
		--if (ded < .85) then
		--	amount = amount * (1 - ded)
		--else
		--	amount = amount * .15
		--end

		local ac_amt = player.armor

		if ac_amt > 0 then
			amount = amount * (1 + (ac_amt * 0.01))

			-- positive AC adds to damage
		else
			amount = amount * (1 - (ac_amt * 0.01))

			-- negative AC reduces damage
		end
	end

	if (ds > 0 and print == 2) then
		amount = amount - player.dmgShield
	elseif (ds == 1) then
		if (player.dmgShield > 0) then
			if (player.dmgShield > amount) then
				player.dmgShield = player.dmgShield - amount
				amount = 0
			else
				amount = amount - player.dmgShield
				player.dmgShield = 0
			end
		else
			amount = amount - player.dmgShield
			player.dmgShield = 0
		end
	elseif (ds == 2) then
		player.dmgShield = player.dmgShield - amount
	end

	amount = -amount

	-- Sama seperti di atas: healer boleh nil. Baris `if (healer ~= nil)` tepat
	-- di bawah membuktikan penulisnya memang menyiapkan kemungkinan itu —
	-- baris ini saja yang terlewat.
	if (healer ~= nil and player.gfxDye ~= healer.gfxDye) then
		if (player.m == 33 or player.m == 3011 or player.m == 3017) then
			amount = 0
		end
	end

	if (healer ~= nil) then
		healer.damage = amount
		healer.critChance = 0
	else
		player.damage = amount
		player.critChance = 0
	end

	if (print == 1) then
		if (player.health - amount > player.maxHealth) then
			player.health = player.maxHealth
			player:updateState()
		else
			player.health = player.health - amount
			player:sendStatus()
		end
	elseif (print == 2) then
		return amount
	else
		-- healer boleh nil, dan cabang `if (healer ~= nil)` beberapa baris di
		-- atas sudah menyiapkan jalurnya: saat penyembuhnya diri sendiri,
		-- angkanya ditulis ke player.damage/critChance. Yang dipakai di sini
		-- mengikuti cabang yang sama, dan player.attacker tidak diubah —
		-- tidak ada penyerang untuk dicatat.
		if (healer ~= nil) then
			player.attacker = healer.ID
			player:sendHealth(math.floor(healer.damage), healer.critChance)
		else
			player:sendHealth(math.floor(player.damage), player.critChance)
		end
		player:sendStatus()
	end
end

function Player.removeHealthWithoutDamageNumbers(player, amount, type)
	local temp_health = 0

	temp_health = player.health - amount

	if temp_health <= 0 then
		temp_health = 0
	end

	player.health = temp_health

	if (player.attacker >= 1073741823) then
		Mob(player.attacker).damage = amount
		Mob(player.attacker).critChance = type
	elseif (player.attacker > 0) then
		Player(player.attacker).damage = amount
		Player(player.attacker).critChance = type
	else
		player.damage = amount
		player.critChance = type
	end

	if (player.health <= 0) then
		onDeathPlayer(player)
	else
		player:sendStatus()
	end
end

function Player.addHealth2(player, amount, type)
	local temp_health
	local temp_amount
	local change_health

	change_health = player.health
	temp_health = player.health + amount

	if temp_health > player.maxHealth then
		player.health = player.maxHealth
	else
		player.health = temp_health
	end
	if temp_health > (math.pow(256, 4) - 1) then
		player.health = player.maxHealth
	end

	if player.health == change_health then
		return false
	end

	player:sendStatus()
end

function Player.removeHealthExtend(player, amount, sleep, deduction, ac, damageShield, print)
	if player:hasDuration("harden_body_poet") or
		player:hasDuration("deaths_guard_poet") or
		player:hasDuration("lifes_protection_poet") or
		player:hasDuration("body_of_alignment_poet") then
		return
	end

	local netAmount = player:calculateNetDamage(amount, sleep, deduction, ac, damageShield, print, 0)
	local attacker

	if (player.attacker >= 1073741823) then
		attacker = Mob(player.attacker)
	elseif (player.attacker > 0) then
		attacker = Player(player.attacker)
	end

	if (attacker ~= nil) then
		attacker.damage = netAmount
		attacker.critChance = 0
	else
		player.damage = netAmount
		player.critChance = 0
	end

	if (print == 1) then
		if (player.health - netAmount <= 0) then
			player.health = 0
			player.state = 1
			player:updateState()
		else
			player.health = player.health - netAmount
			player:sendStatus()
		end
	elseif (print == 2) then
		return netAmount
	else
		player_combat.on_attacked(player, attacker)
		player:sendStatus()
	end
end

function Player.calculateNetDamage(player, amount, sleep, deduction, ac, damageShield, print, resetChanges)
	local originalDmgShield = player.dmgShield
	local originalSleep = player.sleep
	
	if (sleep > 0 and print == 2) then
		amount = amount * player.sleep
	elseif (sleep == 1) then
		amount = amount * player.sleep
		player.sleep = 1
		player.updateState()
	elseif (sleep == 2) then
		amount = amount * player.sleep
	end

	if (deduction == 1) then
		if (player.deduction < 0) then
			amount = 0
		elseif (player.deduction > 0) then
			amount = amount * player.deduction
		end
	end

	if (ac == 1) then
		local armorAmount = player.armor

		if armorAmount < -80 then
			armorAmount = -80
		end

		if armorAmount > 100 then
			armorAmount = 100
		end

		if armorAmount ~= 0 then
			amount = amount * (1 + (armorAmount / 100))
		else
			amount = amount
		end
	end

	if (damageShield > 0 and print == 2) then
		amount = amount - player.dmgShield
	elseif (damageShield == 1) then
		if (player.dmgShield > 0) then
			if (player.dmgShield > amount) then
				player.dmgShield = player.dmgShield - amount
				amount = 0
			else
				amount = amount - player.dmgShield
				player.dmgShield = 0
			end
		else
			amount = amount - player.dmgShield
			player.dmgShield = 0
		end
	elseif (damageShield == 2) then
		player.dmgShield = player.dmgShield - amount
	end

	if (resetChanges ~= 0) then
		player.dmgShield = originalDmgShield
		player.sleep = originalSleep
	end

	return math.ceil(amount)
end

function Player.addMagic(player, amount)
	local magic = player.magic + amount

	if (amount < 0) then
		player:sendMinitext("Ini memakai fungsi yang salah, hubungi GM.")
		return
	end

	if (magic > player.maxMagic) then
		player.magic = player.maxMagic
	else
		player.magic = magic
	end

	player:sendStatus()
end

function Player.removeMagic(player, amount)
	local magic = player.magic - amount

	if (amount < 0) then
		player:sendMinitext("Ini memakai fungsi yang salah, hubungi GM.")
		return
	end

	if (magic < 0) then
		player.magic = 0
	else
		player.magic = magic
	end

	player:sendStatus()
end

function Player.addMagicExtend(player, amount)
	local temp_magic
	local ded = 0

	ded = 1 * string.format(
		"%.2f",
		player.armor / (player.armor + 400 + 95 * (attacker.level + attacker.tier ^ 2 + attacker.mark ^ 3))
	)

	if (player.sleep ~= nil) then
		amount = amount * player.sleep
	end

	if (player.deduction > 0) then
		amount = amount * player.deduction
	end

	if (ded <.85) then
		amount = amount * (1 - ded)
	else
		amount = amount *.15
	end

	temp_magic = player.magic + amount
	if (player.magic < amount or temp_magic <= 0) then
		player.magic = 0
	end

	if temp_magic > player.maxMagic then
		player.magic = player.maxMagic
	else
		player.magic = temp_magic
	end

	if temp_magic > (math.pow(256, 4) - 1) then
		player.magic = player.maxMagic
	end

	player:sendStatus()
end

function Player.addShield(player, shielding, maxShield)
	local shield = player.dmgShield

	if (maxShield == nil or maxShield > player.maxHealth *.5) then
		maxShield = player.maxHealth *.5
	end

	if (shield + shielding > maxShield) then
		player.dmgShield = maxShield
	else
		player.dmgShield = shield + shielding
	end
end

function Player.removeShield(player, unshielding, negative)
	local shield = player.dmgShield

	if (negative == nil and shield - unshielding < 0) then
		player.dmgShield = 0
	else
		player.dmgShield = shield - unshielding
	end
end

--Dialogs and menus

Player.dialogSeq = function(player, commands, continue)
	if (not continue) then
		continue = 1
	end

	local pos = 1

	local messages, state, options =
		{},
		{
			graphic = player.npcGraphic,
			color = player.npcColor
		},
		{}

	for _, command in pairs(commands) do
		if type(command) == "table" then
			state.graphic = command.graphic
			state.color = command.color
		elseif type(command) == "string" then
			table.insert(
				messages,
				{graphic = state.graphic, color = state.color, text = command}
			)
		end
	end

	local choice

	while pos <= #messages do
		if pos ~= 1 then
			table.insert(options, "previous")
		end
		if pos ~= #messages then
			table.insert(options, "next")
		end
		if pos == #messages and continue == 1 then
			table.insert(options, "next")
		end

		player.npcGraphic = messages[pos].graphic
		player.npcColor = messages[pos].color

		if (messages[pos].graphic == 0 and messages[pos].color == 0) then
			player.dialogType = 1
		else
			player.dialogType = 0
		end

		choice = player:dialog(messages[pos].text, options)

		if (choice == "next") then
			pos = pos + 1
		end

		if (choice == "previous") then
			pos = pos - 1
		end

		if (choice == "quit") then
			return false
		end
	end

	return true
end

function Player.menuString(player, message, options)
	if (options == nil) then
		options = {}
	end
	local selection = player:menu(message, options)
	return options[selection]
end

function Player.menuHairFace(player, message, options)
	if (options == nil) then
		options = {}
	end

	local selection = player:hairFaceMenu(message, options)
	return options[selection]
end

function Player.bankItemsList(player, banktype)
	local bankItems = {}

	if banktype == nil then
		banktype = 0
	end

	if banktype == 0 then
		-- player bank
		bankItems = player:getBankItems()
	elseif banktype == 1 then
		-- subpath bank
		bankItems = player:getSubpathBankItems()
	elseif banktype == 2 then
		-- clan bank
		bankItems = player:getClanBankItems()
	end

	local bankItemTable = {}
	local bankCountTable = {}
	local bankOwnerTable = {}
	local bankEngraveTable = {}
	local bankTimerTable = {}
	local bankItemTableNames = {}
	local bankProtectedTable = {}
	local bankCustomIconTable = {}
	local bankCustomIconColorTable = {}
	local bankCustomLookTable = {}
	local bankCustomLookColorTable = {}

	for i = 1, #bankItems, 10 do
		table.insert(bankItemTable, bankItems[i])
		table.insert(bankCountTable, bankItems[i + 1])
		table.insert(bankOwnerTable, bankItems[i + 2])
		table.insert(bankTimerTable, bankItems[i + 3])
		table.insert(bankEngraveTable, bankItems[i + 4])
		table.insert(bankProtectedTable, bankItems[i + 5])
		table.insert(bankCustomIconTable, bankItems[i + 6])
		table.insert(bankCustomIconColorTable, bankItems[i + 7])
		table.insert(bankCustomLookTable, bankItems[i + 8])
		table.insert(bankCustomLookColorTable, bankItems[i + 9])
	end

	for i = 1, #bankItemTable do
		if bankEngraveTable[i] ~= "" then
			table.insert(bankItemTableNames, bankEngraveTable[i])
		else
			table.insert(bankItemTableNames, Item(bankItemTable[i]).name)
		end
	end

	for i = 1, #bankItemTableNames do
		if bankOwnerTable[i] ~= 0 then
			bankItemTableNames[i] = bankItemTableNames[i] .. " - BONDED"
		end
	end

	local sortedbankItemTable = sort_relative(bankItemTableNames, bankItemTable)
	local sortedbankCountTable = sort_relative(
		bankItemTableNames,
		bankCountTable
	)
	local sortedbankOwnerTable = sort_relative(
		bankItemTableNames,
		bankOwnerTable
	)
	local sortedbankEngraveTable = sort_relative(
		bankItemTableNames,
		bankEngraveTable
	)
	local sortedbankTimerTable = sort_relative(
		bankItemTableNames,
		bankTimerTable
	)
	local sortedbankItemTableNames = sort_relative(
		bankItemTableNames,
		bankItemTableNames
	)

	bankItemTable = sortedbankItemTable
	bankCountTable = sortedbankCountTable
	bankOwnerTable = sortedbankOwnerTable
	bankEngraveTable = sortedbankEngraveTable
	bankTimerTable = sortedbankTimerTable
	bankItemTableNames = sortedbankItemTableNames

	return bankItemTable, bankCountTable, bankOwnerTable, bankEngraveTable, bankTimerTable, bankItemTableNames
end

function Player.showBankWithdraw(player, npc, bankOwnerName)
	if (bankOwnerName == nil) then
		bankOwnerName = player.name
	end

	local bankOwner = bank.get_bankOwner(player, bankOwnerName)

	if (bankOwner == nil) then
		return false
	end

	local bankItemTable, bankCountTable, bankOwnerTable, bankEngraveTable, bankTimerTable, bankItemTableNames = bankOwner:bankItemsList(0)

	local found = 0
	local amount = 0

	if (#bankItemTable == 0) then
		player:dialogSeq({bankOwner.name .. "... aku tidak menemukan apa pun untuk akun itu."})
		return false
	end

	local buytext = {}

	for i = 1, #bankItemTable do
		local text = ""
		if bankOwnerTable[i] ~= 0 then
			text = text .. "Owner: " .. getOfflineID(bankOwnerTable[i])
		end

		table.insert(buytext, text)
	end

	local temp = player:buy(
		bankOwner.name .. "... Here's what I have.\nWhat do you want back?",
		bankItemTable,
		bankCountTable,
		bankEngraveTable,
		bankOwnerTable,
		buytext,
		{},
		{}
	)

	for i = 1, #bankItemTable do
		if (bankItemTableNames[i] == temp) then
			found = i
			break
		end
	end

	if (found == 0) then
		return nil
	end

	if (Item(bankItemTable[found]).stackAmount > 1) then
		amount = player:inputNumberCheck(player:input("Berapa banyak yang mau kau ambil?"))
		amount = math.ceil(math.abs(amount))

		if (amount > bankCountTable[found]) then
			amount = bankCountTable[found]
		end
	else
		amount = 1
	end

	if (amount <= 0) then
		return false
	end

	if Item(bankItemTable[found]).maxAmount > 0 then
		local item = {}
		local hasAmount = 0

		for i = 0, 52 do
			item = player:getInventoryItem(i)
			if item ~= nil then
				if item.id == Item(bankItemTable[found]).id then
					hasAmount = item.amount
					break
				end
			end
		end

		if hasAmount >= Item(bankItemTable[found]).maxAmount or hasAmount + amount > Item(bankItemTable[found]).maxAmount then
			player:sendMinitext("(" .. Item(bankItemTable[found]).name .. "). Kau tidak boleh punya lebih dari (" .. Item(bankItemTable[found]).maxAmount .. ").")
			return false
		end
	end

	if (player:hasSpace(
		bankItemTable[found],
		amount,
		bankOwnerTable[found],
		bankEngraveTable[found]
	) ~= true) then
		player:menu(
			"Tanganmu tidak cukup untuk membawa semua itu. Kosongkan sedikit ruang di kantongmu lalu temui aku lagi.",
			{},
			{}
		)
		return false
	end

	local itemName = Item(bankItemTable[found]).name
	local engrave = bankEngraveTable[found]
	local owner = bankOwnerTable[found]

	-- Get bankOwner again to ensure he/she has not logged out since the transaction started
	bankOwner = bank.get_bankOwner(player, bankOwnerName)

	if (bankOwner == nil) then
		return false
	end

	if (bank.is_bank_locked(player, bankOwner)) then
		bank.show_bank_locked(player)
		return false
	end

	local worked = player:addItem(
		bankItemTable[found],
		amount,
		0,
		bankOwnerTable[found],
		bankTimerTable[found],
		bankEngraveTable[found]
	)

	if (worked == true) then
		bankOwner:bankWithdraw(
			bankItemTable[found],
			amount,
			bankOwnerTable[found],
			bankTimerTable[found],
			bankEngraveTable[found]
		)
	else
		player:sendMinitext("Tidak bisa mengambil " .. amount .. " " .. Item(bankItemTable[found]).name .. "(s).")
		return false
	end

	characterLog.withdrawItemWrite(bankOwner, itemName, engrave, amount, owner)

	if amount == 1 then
		npc:talk(
			0,
			npc.name .. ": Ini dia " .. Item(bankItemTable[found]).name .. "."
		)
	elseif amount > 1 then
		npc:talk(
			0,
			npc.name .. ": Ini dia " .. Item(bankItemTable[found]).name .. " (" .. amount .. ")."
		)
	end

	return true
end

function Player.showBankDeposit(player, npc, bankOwnerName)
	if (bankOwnerName == nil) then
		bankOwnerName = player.name
	end

	local bankOwner = bank.get_bankOwner(player, bankOwnerName)

	if (bankOwner == nil) then
		return false
	end
	
	local amountCheck = 0
	local itemTable = {}
	local amount = 0
	local found = 0

	for i = 0, player.maxInv do
		local nItem = player:getInventoryItem(i)

		if (nItem ~= nil and nItem.id > 0) then
			if (#itemTable > 0) then
				found = 0

				for j = 1, #itemTable do
					if (itemTable[j] == nItem.id) then
						found = 1
						break
					end
				end

				if (found == 0) then
					table.insert(itemTable, nItem.id)
				end
			else
				table.insert(itemTable, nItem.id)
			end
		end
	end

	local choice = player:sell(
		"Deposit for " .. bankOwner.name .. ". What would you like me to hold onto?",
		itemTable
	)
	local dItem = player:getInventoryItem(choice - 1)
	local itemName = dItem.name
	local engrave = dItem.realName
	local owner = dItem.owner

	local moneyAmount = 0

	if (dItem == nil) then
		return
	end

	if (dItem.name == "") then
		player:sendMinitext("Barang itu tidak bisa dititipkan.")
		return false
	end

	if (dItem.depositable) then
		player:sendMinitext("Barang itu tidak bisa dititipkan.")
		return false
	end

	if engrave ~= "" then
		player:sendMinitext("Barang berukir tidak bisa dititipkan.")
		return false
	end

	if (dItem.stackAmount > 1 and dItem.amount > 1) then
		amount = player:inputNumberCheck(player:input("Berapa banyak yang ingin kau titipkan?"))

		if (player:hasItem(dItem.id, amount) ~= true) then
			amountCheck = player:hasItem(dItem.id, amount)
			amount = amountCheck
		end
	else
		amount = 1
	end

	if (amount == 0) then
		player:dialogSeq({"Kau tidak bisa menitipkan nol barang."})
		return false
	end

	if (dItem.dura == dItem.maxDura) then

		moneyAmount = math.ceil(dItem.sell * 0.10 * amount)

		if (player:hasItem(dItem.id, amount, dItem.dura, dItem.owner, dItem.realName) == true) then
			if (player.money >= moneyAmount) then
				-- Get bankOwner again to ensure he/she has not logged out since the transaction started
				bankOwner = bank.get_bankOwner(player, bankOwnerName)
			
				if (bankOwner == nil) then
					return false
				end
			
				if (bank.is_bank_locked(player, bankOwner)) then
					bank.show_bank_locked(player)
					return false
				end

				player.money = player.money - moneyAmount
				bankOwner:bankDeposit(
					dItem.id,
					amount,
					dItem.owner,
					dItem.time,
					dItem.realName
				)

				if (amount == 1) then
					player:removeItemSlot((choice - 1), amount, 9)
				else
					player:removeItem(dItem.id, amount, 9)
				end

				player:sendStatus()
			else
				player:dialogSeq({"Uangmu tidak cukup untuk membayar biaya penyimpananku."})
				return false
			end
		else
			player:dialogSeq({"Kau tidak membawa barang yang kau minta kusimpan."})
			return false
		end
	else
		player:sendMinitext("Aku tidak mau rongsokanmu. Minta pandai besi memperbaikinya.")
		return false
	end

	characterLog.depositItemWrite(bankOwner, itemName, engrave, amount, owner)

	npc:talk(
		0,
		npc.name .. ": Aku ambil " .. itemName .. ". " .. amount .. " di antaranya."
	)

	if moneyAmount > 0 then
		npc:talk(0, npc.name .. ": Biayanya " .. moneyAmount .. " keping.")
	end

	return true
end

function Player.bankAddMoney(player, npc, bankOwnerName)
	if (bankOwnerName == nil) then
		bankOwnerName = player.name
	end

	local bankOwner = bank.get_bankOwner(player, bankOwnerName)

	if (bankOwner == nil) then
		return false
	end
	
	local maxamount = 100000000

	local amount = player:inputNumberCheck(player:input(bankOwner.name .. "... catatanku menunjukkan saldo " .. Tools.formatNumber(bankOwner.bankMoney) .. " keping. Berapa lagi yang akan kau titipkan hari ini?"))

	-- Get bankOwner again to ensure he/she has not logged out since the transaction started
	bankOwner = bank.get_bankOwner(player, bankOwnerName)

	if (bankOwner == nil) then
		return false
	end

	if (amount > player.money) then
		amount = player.money
	end
	if (amount > maxamount) then
		amount = maxamount
	end
	if (bankOwner.bankMoney + amount > maxamount) then
		amount = maxamount - bankOwner.bankMoney
	end
	if (bankOwner.bankMoney == maxamount) then
		player:dialogSeq({
			bankOwner.name .. "... sepertinya rekeningnya sudah penuh. Aku tidak bisa menerima lagi. (Batas saat ini: " .. Tools.formatNumber(maxamount) .. ")"
		})
	end

	if (bank.is_bank_locked(player, bankOwner)) then
		bank.show_bank_locked(player)
		return false
	end

	player:removeGold(amount)
	bankOwner.bankMoney = bankOwner.bankMoney + amount

	characterLog.depositGoldWrite(bankOwner, amount)

	npc:talk(0, npc.name .. ": Kau menitipkan " .. amount .. " keping.")

	return true
end

function Player.bankWithdrawMoney(player, npc, bankOwnerName)
	if (bankOwnerName == nil) then
		bankOwnerName = player.name
	end

	local bankOwner = bank.get_bankOwner(player, bankOwnerName)

	if (bankOwner == nil) then
		return false
	end

	local inBank = bankOwner.bankMoney

	if (inBank <= 0) then
		player:dialogSeq({bankOwner.name .. "... Maaf, sepertinya rekeningnya sedang kosong."})
		return
	end

	local amount = player:inputNumberCheck(player:input(bankOwner.name .. "... catatanku menunjukkan saldo " .. Tools.formatNumber(inBank) .. " keping. Berapa banyak yang akan kau ambil?"))

	if (amount > inBank) then
		amount = inBank
	end

	-- Get bankOwner again to ensure he/she has not logged out since the transaction started
	bankOwner = bank.get_bankOwner(player, bankOwnerName)

	if (bankOwner == nil) then
		return false
	end

	if (bank.is_bank_locked(player, bankOwner)) then
		bank.show_bank_locked(player)
		return false
	end

	player:addGold(amount)
	bankOwner.bankMoney = bankOwner.bankMoney - amount

	characterLog.withdrawGoldWrite(bankOwner, amount)

	npc:talk(0, npc.name .. ": Ini dia " .. amount .. " keping.")
	player:sendStatus()
end

function Player.inventorySelect(player, npc, dialog, items, prices)
	if (items == nil) then
		items = {}
	end
	if (prices == nil) then
		prices = {}
	end

	local amount = 1
	local choice = player:sell(dialog, items)
	local sell = player:getInventoryItem(choice - 1)
	local price = 0

	local sell_item = {
		id = sell.id,
		dura = sell.dura,
		maxDura = sell.maxDura,
		sell = sell.sell,
		name = sell.name,
		amount = sell.amount
	}
	if (sell_item.amount > 1) then
		amount = player:inputNumberCheck(player:input("Berapa banyak yang ingin kau pakai?"))

		if (amount > 4000000000 or player:hasItem(sell_item.id, amount) ~= true) then
			player:sendMinitext("Kau tidak bisa melakukan itu.")
			return
		end
	else
		amount = 1
	end

	local x = 0
	for i = 1, #items do
		if sell.yname == items[i] or sell.id == items[i] and prices ~= nil then
			price = prices[i]
		end
	end

	if price ~= nil then
		sell_item.sell = price
	end

	local duracheck = 1
	if (sell_item.maxDura ~= 0) then
		if (sell_item.dura ~= sell_item.maxDura) then
			player:dialogSeq({"Maaf, aku tidak mau berurusan dengan barangmu yang rusak."})
			return
		end
	else
		duracheck = 1
	end

	local cost = math.floor(sell_item.sell * amount * duracheck)

	local choice = player:menuString("Pastikan pilihan barang", {"Ya", "Tidak"})
	if (choice == "Ya") then
		local selltable = {sell_item.id, amount}
		return selltable
	end
end

function Player.showClanBankWithdraw(player, npc)
	local bankItemTable, bankCountTable, bankOwnerTable, bankEngraveTable, bankTimerTable, bankItemTableNames = player:bankItemsList(2)

	-- type 2 = clan bank

	local found = 0
	local amount = 0
	local counter = 0
	local next = next

	if (#bankItemTable == 0) then
		player:dialogSeq({"Simpanan klanmu sedang kosong."})
		return false
	end

	local buytext = {}

	for i = 1, #bankItemTable do
		local text = ""
		if bankOwnerTable[i] ~= 0 then
			text = text .. "Owner: " .. getOfflineID(bankOwnerTable[i])
		end

		--[[if Item(bankItemTable[i]).maxDura == 0 then
			text = text .. " | Prot: "..auctionItmProtected[i]
		else
			text = text .. " ("..((auctionItmDurability[i]/Item(auctionItmIds[i]).maxDura)*100)..")% | Prot: "..auctionItmProtected[i]
		end]]
		--

		table.insert(buytext, text)
	end

	local temp = player:buy(
		"Here's what I've been holding for your clan.\nWhat do you want back?",
		bankItemTable,
		bankCountTable,
		bankEngraveTable,
		bankOwnerTable,
		buytext,
		{},
		{}
	)

	for i = 1, #bankItemTable do
		if (bankItemTableNames[i] == temp) then
			found = i
			break
		end
	end

	if (found == 0) then
		return nil
	end

	if (Item(bankItemTable[found]).stackAmount > 1) then
		amount = player:inputNumberCheck(player:input("Berapa banyak yang mau kau ambil?"))
		amount = math.ceil(math.abs(amount))

		if (amount > bankCountTable[found]) then
			amount = bankCountTable[found]
		end
	else
		amount = 1
	end

	if (amount <= 0) then
		return false
	end

	if Item(bankItemTable[found]).maxAmount > 0 then
		local item = {}
		local hasAmount = 0

		for i = 0, 52 do
			item = player:getInventoryItem(i)
			if item ~= nil then
				if item.id == Item(bankItemTable[found]).id then
					hasAmount = item.amount
					break
				end
			end
		end

		--if hasAmount == 0 then player:popUp("Bug encountered. Contact TKR staff.") return end

		if hasAmount >= Item(bankItemTable[found]).maxAmount or hasAmount + amount > Item(bankItemTable[found]).maxAmount then
			player:sendMinitext("(" .. Item(bankItemTable[found]).name .. "). Kau tidak boleh punya lebih dari (" .. Item(bankItemTable[found]).maxAmount .. ").")
			return
		end
	end

	if (player:hasSpace(
		bankItemTable[found],
		amount,
		bankOwnerTable[found],
		bankEngraveTable[found]
	) ~= true) then
		player:menu(
			"Tanganmu tidak cukup untuk membawa semua itu. Kosongkan sedikit ruang di kantongmu lalu temui aku lagi.",
			{},
			{}
		)
		return false
	end

	local itemName = Item(bankItemTable[found]).name
	local engrave = bankEngraveTable[found]
	local owner = bankOwnerTable[found]

	local worked = player:addItem(
		bankItemTable[found],
		amount,
		0,
		bankOwnerTable[found],
		bankTimerTable[found],
		bankEngraveTable[found]
	)

	if (worked == true) then
		player:clanBankWithdraw(
			bankItemTable[found],
			amount,
			bankOwnerTable[found],
			bankTimerTable[found],
			bankEngraveTable[found]
		)
	else
		player:sendMinitext("Tidak bisa mengambil " .. amount .. " " .. Item(bankItemTable[found]).name .. "(s).")
		return false
	end

	characterLog.withdrawItemWrite(player, itemName, engrave, amount, owner)

	if amount == 1 then
		npc:talk(
			0,
			npc.name .. ": Ini dia " .. Item(bankItemTable[found]).name .. "."
		)
	elseif amount > 1 then
		npc:talk(
			0,
			npc.name .. ": Ini dia " .. Item(bankItemTable[found]).name .. " (" .. amount .. ")."
		)
	end

	return true
end

function Player.showClanBankDeposit(player, npc)
	local amountCheck = 0
	local itemTable = {}
	local amount = 0
	local found = 0

	for i = 0, player.maxInv do
		local nItem = player:getInventoryItem(i)

		if (nItem ~= nil and nItem.id > 0) then
			if (#itemTable > 0) then
				found = 0

				for j = 1, #itemTable do
					if (itemTable[j] == nItem.id) then
						found = 1
						break
					end
				end

				if (found == 0) then
					table.insert(itemTable, nItem.id)
				end
			else
				table.insert(itemTable, nItem.id)
			end
		end
	end

	local choice = player:sell(
		"What would you like me to deposit in your clan's bank?",
		itemTable
	)
	local dItem = player:getInventoryItem(choice - 1)
	local itemName = dItem.name
	local engrave = dItem.realName
	local owner = dItem.owner

	local moneyAmount = 0

	if (dItem == nil) then
		return
	end

	if (dItem.name == "") then
		player:sendMinitext("Barang itu tidak bisa dititipkan.")
		return false
	end

	if (dItem.depositable) then
		player:sendMinitext("Barang itu tidak bisa dititipkan.")
		return false
	end

	if (dItem.stackAmount > 1 and dItem.amount > 1) then
		amount = player:inputNumberCheck(player:input("Berapa banyak yang ingin kau titipkan?"))

		if (player:hasItem(dItem.id, amount) ~= true) then
			amountCheck = player:hasItem(dItem.id, amount)
			amount = amountCheck
		end
	else
		amount = 1
	end

	if (amount == 0) then
		player:dialogSeq({"Kau mencoba menyerahkan sesuatu yang tidak ada, dan gagal."})
		return false
	end

	if (dItem.dura == dItem.maxDura) then
		local moneyChoice = ""

		moneyAmount = math.ceil(dItem.sell * 0.10 * amount)

		if (player:hasItem(dItem.id, amount, dItem.dura, dItem.owner, dItem.realName) == true) then
			if (player.money >= moneyAmount) then
				player.money = player.money - moneyAmount
				player:clanBankDeposit(
					dItem.id,
					amount,
					dItem.owner,
					dItem.time,
					dItem.realName
				)

				if (amount == 1) then
					player:removeItemSlot((choice - 1), amount, 9)
				else
					player:removeItem(dItem.id, amount, 9)
				end

				player:sendStatus()
			else
				player:dialogSeq({"Uangmu tidak cukup untuk membayar biaya penyimpananku."})
				return false
			end
		else
			player:dialogSeq({"Kau tidak membawa barang yang ingin kau titipkan padaku."})
			return false
		end
	else
		player:sendMinitext("Aku tidak mau rongsokanmu. Minta pandai besi memperbaikinya.")
		return false
	end

	characterLog.depositItemWrite(player, itemName, engrave, amount, owner)

	npc:talk(
		0,
		npc.name .. ": Aku ambil " .. itemName .. ". " .. amount .. " di antaranya."
	)

	if moneyAmount > 0 then
		npc:talk(0, npc.name .. ": Biayanya " .. moneyAmount .. " keping.")
	end

	return true
end

function Player.sellExtend(player, dialog, items, prices)
	if (items == nil) then
		items = {}
	end
	if (prices == nil) then
		prices = {}
	end

	local amount = 1
	local choice = player:sell(dialog, items)
	local sell = player:getInventoryItem(choice - 1)
	local price = 0

	local sell_item = {
		id = sell.id,
		dura = sell.dura,
		maxDura = sell.maxDura,
		sell = sell.sell,
		name = sell.name,
		amount = sell.amount
	}
	if (sell_item.amount > 1) then
		amount = player:inputNumberCheck(player:input("Berapa banyak yang ingin kau jual?"))

		if (amount > 4000000000 or player:hasItem(sell_item.id, amount) ~= true) then
			player:sendMinitext("Kau tidak bisa melakukan itu.")
			return
		end
	else
		amount = 1
	end

	local x = 0
	for i = 1, #items do
		if sell.yname == items[i] or sell.id == items[i] and prices ~= nil then
			price = prices[i]
		end
	end

	if price ~= nil then
		sell_item.sell = price
	end

	--if (sell.owner > 0 or sell.realName ~= "") then
	--	player:dialogSeq({"I am sorry, but I am not interested in personal items."})
	--	return
	--end

	local duracheck = 1
	if (sell_item.maxDura ~= 0) then
		if (sell_item.dura ~= sell_item.maxDura) then
			player:dialogSeq({"Maaf, aku tidak mau berurusan dengan barangmu yang rusak."})
			return
		end
	else
		duracheck = 1
	end

	local cost = math.floor(sell_item.sell * amount * duracheck)

	local choice = player:menuString(
		"Aku akan membeli " .. amount .. " " .. sell_item.name .. " seharga " .. cost .. " emas. Sepakat?",
		{"Ya", "Tidak"}
	)
	if (choice == "Ya") then
		if (amount > 4000000000 or player:hasItem(sell_item.id, amount) ~= true) then
			player:sendMinitext("Kau tidak bisa melakukan itu.")
			return
		end

		local selltable = {sell_item.id, amount}
		player:removeItem(sell_item.id, amount, 10)
		player:addGold(cost)
		characterLog.sellItemWrite(player, sell_item, amount, cost)

		return selltable
	end
end

function Player.buyExtend(player, dialog, items, prices, maxamounts)
	if (items == nil) then
		items = {}
	end
	if (prices == nil) then
		prices = {}
	end
	local x = 0
	local amount = 1
	local maxAmount = 0
	local t = {graphics = player.npcGraphic, color = player.npcColor}
	if #prices == 0 then
		for i = 1, #items do
			table.insert(prices, Item(items[i]).price)
		end
	end

	local choice = player:buy(dialog, items, prices, {}, {}, {}, {}, {})

	for i = 1, #items do
		item = Item(items[i])

		if (Item(items[i]).name == choice) then
			x = i
			break
		end
	end
	if (x == 0) then
		return nil
	end

	if (item.stackAmount > 1) then
		amount = player:inputNumberCheck(player:input("Berapa banyak yang kau mau?"))
	end
	if (amount < 1) then
		return
	end

	if maxamounts ~= nil then
		if maxamounts[x] ~= nil and maxamounts[x] > 0 and maxamounts[x] < amount then
			player:dialogSeq(
				{
					t,
					"Aku hanya bisa menjual " .. maxamounts[x] .. " lagi " .. Item(choice).name .. "."
				},
				0
			)
			return
		end
	end

	if (player.money < (prices[x] * amount)) then
		player:dialog("Uangmu tidak cukup.", {})
		return nil
	end
	amount = math.abs(amount)
	local cost = prices[x] * amount

	local newChoice = player:menuString(
		"Aku akan menjual " .. amount .. " " .. Item(choice).name .. " seharga " .. cost .. " emas. Sepakat?",
		{"Ya", "Tidak"}
	)
	if (newChoice == "Ya") then
		if (player:hasSpace(Item(choice).name, amount) and (player.money >= cost)) then
			local buytable = {Item(choice).id, amount}
			player:addItem(Item(choice).name, amount)
			player:removeGold(cost)

			characterLog.buyItemWrite(player, Item(choice), amount, cost)

			return buytable
		else
			player:menu(
				"Tanganmu tidak cukup untuk membawa semua itu. Kosongkan sedikit ruang di kantongmu lalu temui aku lagi.",
				{},
				{}
			)
		end
	end
end

function Player.kanBuyExtend(player, dialog, items, prices, maxamounts)
	if (items == nil) then
		items = {}
	end
	if (prices == nil) then
		prices = {}
	end
	local x = 0
	local amount = 1
	local t = {graphics = player.npcGraphic, color = player.npcColor}
	if #prices == 0 then
		for i = 1, #items do
			table.insert(prices, Item(items[i]).price)
		end
	end

	local choice = player:buy(dialog, items, prices, {}, {}, {}, {}, {})

	for i = 1, #items do
		item = Item(items[i])
		if (Item(items[i]).name == choice) then
			x = i
			break
		end
	end
	if (x == 0) then
		return nil
	end

	if (item.stackAmount > 1) then
		amount = player:inputNumberCheck(player:input("Berapa banyak yang kau mau?"))
	end
	if (amount < 1) then
		return
	end

	if (maxamounts ~= nil and maxamounts[x] ~= nil and maxamounts[x] < amount) then
		player:dialog(
			"Aku hanya bisa menjual " .. maxamounts[x] .. " lagi " .. Item(choice).name .. ".",
			t
		)
	end

	if (player.registry["kan"] < (prices[x] * amount)) then
		player:dialog("Kan-mu tidak cukup.", {})
		return nil
	end
	amount = math.abs(amount)
	local cost = prices[x] * amount

	local newChoice = player:menuString(
		"Aku akan menjual " .. amount .. " " .. Item(choice).name .. " seharga " .. Tools.formatNumber(cost) .. " Kan. Deal?",
		{"Ya", "Tidak"}
	)
	if (newChoice == "Ya") then
		if player:hasSpace(Item(choice).name, amount) then
			if player.registry["kan"] < cost then
				player:menu("Kan-mu tidak cukup.", {}, {})
				return
			end

			local buytable = {Item(choice).id, amount}

			characterLog.kanLogs(player, Item(choice), amount)
			KanNpc.removeKan(player, cost)
			player:addItem(Item(choice).name, amount)

			return buytable
		else
			player:menu(
				"Tanganmu tidak cukup untuk membawa semua itu. Kosongkan sedikit ruang di kantongmu lalu temui aku lagi.",
				{},
				{}
			)
		end
	end
end

function Player.repairExtend(player)
	local choice = 0
	local chosenItem = 0
	local chosenItemDura = 0

	local list = {}
	for i = 0, player.maxInv do
		item = player:getInventoryItem(i)
		if item ~= nil then
			if item.type >= 3 and item.type <= 15 then
				if item.dura < item.maxDura then
					table.insert(list, item.id)
				end
			end
		end
	end

	if next(list) == nil then
		player:menuSeq(
			"Aku tidak bisa membuat barangmu lebih baik daripada sekarang.",
			{"Kembali"},
			{}
		)
		return
	end

	ask = player:sell("What equipment do you need fixed?", list)
	choice = player:getInventoryItem(ask - 1)
	chosenItem = choice.id
	chosenItemDura = choice.dura
	if choice ~= nil and choice.dura < choice.maxDura then
		icon = {graphic = choice.icon, color = choice.iconC}
		if choice.repairable == 0 then
			player:dialogSeq({icon, "Maaf, barang ini tidak bisa diperbaiki!"})
			return
		end
		cost = math.ceil(((choice.price / choice.maxDura) * (choice.maxDura - choice.dura)) * 0.5)
		confirm = player:menuString(
			"Aku butuh setidaknya " .. cost .. " emas untuk memperbaikinya, setuju?",
			{"Ya", "Tidak"}
		)
		if confirm == "Ya" then
			if choice.id == chosenItem and choice.dura == chosenItemDura then
				if player:removeGold(cost) == true or cost == 0 then
					choice.dura = choice.maxDura
					player:updateInv()
					player:menuSeq(
						"Klank  Klank...\nEverything's done.\n\nTotal Cost: " .. cost .. " Keping",
						{"Kembali"},
						{}
					)
				else
					player:menuSeq(
						"Ini pekerjaanku, dan tidak ringan. Aku harus dibayar. Kembalilah kalau emasmu cukup untuk membayar jasaku.",
						{"Kembali"},
						{}
					)
				end
			else
				player:dialogSeq({icon, txt .. "Tunggu, ada yang tidak beres. Kembalilah nanti."})
			end
		end
	end
end

function Player.repairAll(player, npc)
	--player:freeAsync()
	local cost = 0
	local total = 0

	local playerFaceAcc = player:getEquippedItem(EQ_FACEACC)
	local playerHelm = player:getEquippedItem(EQ_HELM)
	local playerCrown = player:getEquippedItem(EQ_CROWN)
	local playerWeapon = player:getEquippedItem(EQ_WEAP)
	local playerArmor = player:getEquippedItem(EQ_ARMOR)
	local playerShield = player:getEquippedItem(EQ_SHIELD)
	local playerLeft = player:getEquippedItem(EQ_LEFT)
	local playerRight = player:getEquippedItem(EQ_RIGHT)
	local playerMantle = player:getEquippedItem(EQ_MANTLE)
	local playerSubLeft = player:getEquippedItem(EQ_SUBLEFT)
	local playerSubRight = player:getEquippedItem(EQ_SUBRIGHT)
	local playerNecklace = player:getEquippedItem(EQ_NECKLACE)
	local playerBoots = player:getEquippedItem(EQ_BOOTS)
	local playerCoat = player:getEquippedItem(EQ_COAT)

	local playerFaceAccCost = 0
	local playerHelmCost = 0
	local playerCrownCost = 0
	local playerWeaponCost = 0
	local playerArmorCost = 0
	local playerShieldCost = 0
	local playerLeftCost = 0
	local playerRightCost = 0
	local playerMantleCost = 0
	local playerSubLeftCost = 0
	local playerSubRightCost = 0
	local playerNecklaceCost = 0
	local playerBootsCost = 0
	local playerCoatCost = 0
	local inventoryItemsCost = 0
	local totalCost = 0

	local inventoryItems = {}

	if (playerFaceAcc ~= nil) then
		if (playerFaceAcc.repairable == 1) then
			if (playerFaceAcc.dura < playerFaceAcc.maxDura) then
				playerFaceAccCost = math.ceil(((playerFaceAcc.price / playerFaceAcc.maxDura) * (playerFaceAcc.maxDura - playerFaceAcc.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerFaceAcc.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerHelm ~= nil) then
		if (playerHelm.repairable == 1) then
			if (playerHelm.dura < playerHelm.maxDura) then
				playerHelmCost = math.ceil(((playerHelm.price / playerHelm.maxDura) * (playerHelm.maxDura - playerHelm.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerHelm.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerCrown ~= nil) then
		if (playerCrown.repairable == 1) then
			if (playerCrown.dura < playerCrown.maxDura) then
				playerCrownCost = math.ceil(((playerCrown.price / playerCrown.maxDura) * (playerCrown.maxDura - playerCrown.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerCrown.name .. " bukan barang yang bisa diperbaiki.")
		end
	end
	if (playerWeapon ~= nil) then
		if (playerWeapon.repairable == 1) then
			if (playerWeapon.dura < playerWeapon.maxDura) then
				playerWeaponCost = math.ceil(((playerWeapon.price / playerWeapon.maxDura) * (playerWeapon.maxDura - playerWeapon.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerWeapon.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerArmor ~= nil) then
		if (playerArmor.repairable == 1) then
			if (playerArmor.dura < playerArmor.maxDura) then
				playerArmorCost = math.ceil(((playerArmor.price / playerArmor.maxDura) * (playerArmor.maxDura - playerArmor.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerArmor.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerShield ~= nil) then
		if (playerShield.repairable == 1) then
			if (playerShield.dura < playerShield.maxDura) then
				playerShieldCost = math.ceil(((playerShield.price / playerShield.maxDura) * (playerShield.maxDura - playerShield.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerShield.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerLeft ~= nil) then
		if (playerLeft.repairable == 1) then
			if (playerLeft.dura < playerLeft.maxDura) then
				playerLeftCost = math.ceil(((playerLeft.price / playerLeft.maxDura) * (playerLeft.maxDura - playerLeft.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerLeft.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerRight ~= nil) then
		if (playerRight.repairable == 1) then
			if (playerRight.dura < playerRight.maxDura) then
				playerRightCost = math.ceil(((playerRight.price / playerRight.maxDura) * (playerRight.maxDura - playerRight.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerRight.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerMantle ~= nil) then
		if (playerMantle.repairable == 1) then
			if (playerMantle.dura < playerMantle.maxDura) then
				playerMantleCost = math.ceil(((playerMantle.price / playerMantle.maxDura) * (playerMantle.maxDura - playerMantle.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerMantle.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerSubLeft ~= nil) then
		if (playerSubLeft.repairable == 1) then
			if (playerSubLeft.dura < playerSubLeft.maxDura) then
				playerSubLeftCost = math.ceil(((playerSubLeft.price / playerSubLeft.maxDura) * (playerSubLeft.maxDura - playerSubLeft.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerSubLeft.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerSubRight ~= nil) then
		if (playerSubRight.repairable == 1) then
			if (playerSubRight.dura < playerSubRight.maxDura) then
				playerSubRightCost = math.ceil(((playerSubRight.price / playerSubRight.maxDura) * (playerSubRight.maxDura - playerSubRight.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerSubRight.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerNecklace ~= nil) then
		if (playerNecklace.repairable == 1) then
			if (playerNecklace.dura < playerNecklace.maxDura) then
				playerNecklaceCost = math.ceil(((playerNecklace.price / playerNecklace.maxDura) * (playerNecklace.maxDura - playerNecklace.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerNecklace.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerBoots ~= nil) then
		if (playerBoots.repairable == 1) then
			if (playerBoots.dura < playerBoots.maxDura) then
				playerBootsCost = math.ceil(((playerBoots.price / playerBoots.maxDura) * (playerBoots.maxDura - playerBoots.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerBoots.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerCoat ~= nil) then
		if (playerCoat.repairable == 1) then
			if (playerCoat.dura < playerCoat.maxDura) then
				playerCoatCost = math.ceil(((playerCoat.price / playerCoat.maxDura) * (playerCoat.maxDura - playerCoat.dura)) * 0.5)
			end
		else
			player:sendMinitext("" .. playerCoat.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	for x = 0, 52 do
		invItems = player:getInventoryItem(x)
		if invItems ~= nil then
			if (invItems ~= nil and invItems.type > 2 and invItems.type < 17) then
				if (invItems.repairable == 1) then
					if (invItems.dura < invItems.maxDura) then
						table.insert(inventoryItems, invItems)
					end
				else
					player:sendMinitext("" .. invItems.name .. " bukan barang yang bisa diperbaiki.")
				end
			end
		end
	end

	if #inventoryItems > 0 then
		for i = 1, #inventoryItems do
			inventoryItemsCost = inventoryItemsCost + math.ceil(((inventoryItems[i].price / inventoryItems[i].maxDura) * (inventoryItems[i].maxDura - inventoryItems[i].dura)) * 0.5)
		end
	end

	totalCost = (playerFaceAccCost + playerHelmCost + playerCrownCost + playerWeaponCost + playerArmorCost + playerShieldCost + playerLeftCost + playerRightCost + playerMantleCost + playerSubLeftCost + playerSubRightCost + playerNecklaceCost + playerBootsCost + playerCoatCost + inventoryItemsCost)

	if totalCost > 0 then
		menu = player:menuString(
			"Coba lihat... jadinya sekitar " .. totalCost .. " emas untuk semuanya. Kau bersedia membayar?",
			{"Ya", "Tidak"}
		)
	elseif totalCost == 0 then
		menu = player:menuString(
			"Coba lihat... semuanya gratis. Lanjutkan?",
			{"Ya", "Tidak"}
		)
	end

	if menu == "Ya" then
		if player:removeGold(totalCost) == true then
			if playerFaceAcc ~= nil then
				if playerFaceAcc.repairable == 1 then
					playerFaceAcc.dura = playerFaceAcc.maxDura
					playerFaceAcc.repairCheck = 0
				end
			end

			if playerHelm ~= nil then
				if playerHelm.repairable == 1 then
					playerHelm.dura = playerHelm.maxDura
					playerHelm.repairCheck = 0
				end
			end

			if playerCrown ~= nil then
				if playerCrown.repairable == 1 then
					playerCrown.dura = playerCrown.maxDura
					playerCrown.repairCheck = 0
				end
			end

			if playerWeapon ~= nil then
				if playerWeapon.repairable == 1 then
					playerWeapon.dura = playerWeapon.maxDura
					playerWeapon.repairCheck = 0
				end
			end

			if playerArmor ~= nil then
				if playerArmor.repairable == 1 then
					playerArmor.dura = playerArmor.maxDura
					playerArmor.repairCheck = 0
				end
			end

			if playerShield ~= nil then
				if playerShield.repairable == 1 then
					playerShield.dura = playerShield.maxDura
					playerShield.repairCheck = 0
				end
			end

			if playerLeft ~= nil then
				if playerLeft.repairable == 1 then
					playerLeft.dura = playerLeft.maxDura
					playerLeft.repairCheck = 0
				end
			end

			if playerRight ~= nil then
				if playerRight.repairable == 1 then
					playerRight.dura = playerRight.maxDura
					playerRight.repairCheck = 0
				end
			end

			if playerMantle ~= nil then
				if playerMantle.repairable == 1 then
					playerMantle.dura = playerMantle.maxDura
					playerMantle.repairCheck = 0
				end
			end

			if playerSubLeft ~= nil then
				if playerSubLeft.repairable == 1 then
					playerSubLeft.dura = playerSubLeft.maxDura
					playerSubLeft.repairCheck = 0
				end
			end

			if playerSubRight ~= nil then
				if playerSubRight.repairable == 1 then
					playerSubRight.dura = playerSubRight.maxDura
					playerSubRight.repairCheck = 0
				end
			end

			if playerNecklace ~= nil then
				if playerNecklace.repairable == 1 then
					playerNecklace.dura = playerNecklace.maxDura
					playerNecklace.repairCheck = 0
				end
			end

			if playerBoots ~= nil then
				if playerBoots.repairable == 1 then
					playerBoots.dura = playerBoots.maxDura
					playerBoots.repairCheck = 0
				end
			end

			if playerCoat ~= nil then
				if playerCoat.repairable == 1 then
					playerCoat.dura = playerCoat.maxDura
					playerCoat.repairCheck = 0
				end
			end

			if #inventoryItems > 0 then
				for i = 1, #inventoryItems do
					if inventoryItems[i].repairable == 1 then
						inventoryItems[i].dura = inventoryItems[i].maxDura

						--player:sendMinitext(""..inventoryItems[i].name.." repaired for "..inventoryCost.." coins.")
						inventoryItems[i].repairCheck = 0
					end
				end
			end
		else
			player:menuSeq(
				"Ini pekerjaanku, dan tidak ringan. Aku harus dibayar. Kembalilah kalau emasmu cukup untuk membayar jasaku.",
				{"Kembali"},
				{}
			)
			return
		end

		player:sendStatus()
		player:updateState()
		player:updateInv()

		if totalCost > 0 then
			player:menuSeq(
				"Klank  Klank...\nEverything's done.\n\nTotal Cost: " .. totalCost .. " Keping",
				{"Kembali"},
				{}
			)
		elseif totalCost == 0 then
			player:menuSeq(
				"Klang  Klang...\nSemuanya selesai.\n\nTotal Biaya: gratis",
				{"Kembali"},
				{}
			)
		end
	else
	end
end

--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
function Player.repairAllNoConfirm(player, npc)
	player:freeAsync()
	local cost = 0
	local total = 0

	local playerFaceAcc = player:getEquippedItem(EQ_FACEACC)
	local playerHelm = player:getEquippedItem(EQ_HELM)
	local playerCrown = player:getEquippedItem(EQ_CROWN)
	local playerWeapon = player:getEquippedItem(EQ_WEAP)
	local playerArmor = player:getEquippedItem(EQ_ARMOR)
	local playerShield = player:getEquippedItem(EQ_SHIELD)
	local playerLeft = player:getEquippedItem(EQ_LEFT)
	local playerRight = player:getEquippedItem(EQ_RIGHT)
	local playerMantle = player:getEquippedItem(EQ_MANTLE)
	local playerSubLeft = player:getEquippedItem(EQ_SUBLEFT)
	local playerSubRight = player:getEquippedItem(EQ_SUBRIGHT)
	local playerNecklace = player:getEquippedItem(EQ_NECKLACE)
	local playerBoots = player:getEquippedItem(EQ_BOOTS)
	local playerCoat = player:getEquippedItem(EQ_COAT)

	local playerFaceAccCost = 0
	local playerHelmCost = 0
	local playerCrownCost = 0
	local playerWeaponCost = 0
	local playerArmorCost = 0
	local playerShieldCost = 0
	local playerLeftCost = 0
	local playerRightCost = 0
	local playerMantleCost = 0
	local playerSubLeftCost = 0
	local playerSubRightCost = 0
	local playerNecklaceCost = 0
	local playerBootsCost = 0
	local playerCoatCost = 0
	local inventoryCost = 0
	local inventoryItemsCost = 0
	local totalCost = 0

	local inventoryItems = {}

	-- That <item name> you're holdiing has taken quite a beating. It'll cost at least <amount> gold to repair.
	-- Your <item name> is pretty worn out, I'll need at least 17 gold to repair it.

	if (playerFaceAcc ~= nil) then
		if (playerFaceAcc.repairable == 1) then
			if (playerFaceAcc.dura < playerFaceAcc.maxDura) then
				playerFaceAccCost = math.ceil(((playerFaceAcc.price / playerFaceAcc.maxDura) * (playerFaceAcc.maxDura - playerFaceAcc.dura)) * 0.5)

				if player.money < playerFaceAccCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerFaceAcc.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerFaceAccCost .. " emas untuk memperbaikinya."
					)
				else
					playerFaceAcc.dura = playerFaceAcc.maxDura
					playerFaceAcc.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerFaceAcc.name .. " seharga " .. playerFaceAccCost .. " emas."
					)
					player.money = player.money - playerFaceAccCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerFaceAcc.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerHelm ~= nil) then
		if (playerHelm.repairable == 1) then
			if (playerHelm.dura < playerHelm.maxDura) then
				playerHelmCost = math.ceil(((playerHelm.price / playerHelm.maxDura) * (playerHelm.maxDura - playerHelm.dura)) * 0.5)

				if player.money < playerHelmCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerHelm.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerHelmCost .. " emas untuk memperbaikinya."
					)
				else
					playerHelm.dura = playerHelm.maxDura
					playerHelm.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerHelm.name .. " seharga " .. playerHelmCost .. " emas."
					)
					player.money = player.money - playerHelmCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerHelm.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerCrown ~= nil) then
		if (playerCrown.repairable == 1) then
			if (playerCrown.dura < playerCrown.maxDura) then
				playerCrownCost = math.ceil(((playerCrown.price / playerCrown.maxDura) * (playerCrown.maxDura - playerCrown.dura)) * 0.5)

				if player.money < playerCrownCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerCrown.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerCrownCost .. " emas untuk memperbaikinya."
					)
				else
					playerCrown.dura = playerCrown.maxDura
					playerCrown.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerCrown.name .. " seharga " .. playerCrownCost .. " emas."
					)
					player.money = player.money - playerCrownCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerCrown.name .. " bukan barang yang bisa diperbaiki.")
		end
	end
	if (playerWeapon ~= nil) then
		if (playerWeapon.repairable == 1) then
			if (playerWeapon.dura < playerWeapon.maxDura) then
				playerWeaponCost = math.ceil(((playerWeapon.price / playerWeapon.maxDura) * (playerWeapon.maxDura - playerWeapon.dura)) * 0.5)

				if player.money < playerWeaponCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerWeapon.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerWeaponCost .. " emas untuk memperbaikinya."
					)
				else
					playerWeapon.dura = playerWeapon.maxDura
					playerWeapon.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerWeapon.name .. " seharga " .. playerWeaponCost .. " emas."
					)
					player.money = player.money - playerWeaponCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerWeapon.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerArmor ~= nil) then
		if (playerArmor.repairable == 1) then
			if (playerArmor.dura < playerArmor.maxDura) then
				playerArmorCost = math.ceil(((playerArmor.price / playerArmor.maxDura) * (playerArmor.maxDura - playerArmor.dura)) * 0.5)

				if player.money < playerArmorCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerArmor.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerArmorCost .. " emas untuk memperbaikinya."
					)
				else
					playerArmor.dura = playerArmor.maxDura
					playerArmor.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerArmor.name .. " seharga " .. playerArmorCost .. " emas."
					)
					player.money = player.money - playerArmorCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerArmor.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerShield ~= nil) then
		if (playerShield.repairable == 1) then
			if (playerShield.dura < playerShield.maxDura) then
				playerShieldCost = math.ceil(((playerShield.price / playerShield.maxDura) * (playerShield.maxDura - playerShield.dura)) * 0.5)

				if player.money < playerShieldCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerShield.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerShieldCost .. " emas untuk memperbaikinya."
					)
				else
					playerShield.dura = playerShield.maxDura
					playerShield.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerShield.name .. " seharga " .. playerShieldCost .. " emas."
					)
					player.money = player.money - playerShieldCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerShield.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerLeft ~= nil) then
		if (playerLeft.repairable == 1) then
			if (playerLeft.dura < playerLeft.maxDura) then
				playerLeftCost = math.ceil(((playerLeft.price / playerLeft.maxDura) * (playerLeft.maxDura - playerLeft.dura)) * 0.5)

				if player.money < playerLeftCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerLeft.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerLeftCost .. " emas untuk memperbaikinya."
					)
				else
					playerLeft.dura = playerLeft.maxDura
					playerLeft.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerLeft.name .. " seharga " .. playerLeftCost .. " emas."
					)
					player.money = player.money - playerLeftCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerLeft.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerRight ~= nil) then
		if (playerRight.repairable == 1) then
			if (playerRight.dura < playerRight.maxDura) then
				playerRightCost = math.ceil(((playerRight.price / playerRight.maxDura) * (playerRight.maxDura - playerRight.dura)) * 0.5)

				if player.money < playerRightCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerRight.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerRightCost .. " emas untuk memperbaikinya."
					)
				else
					playerRight.dura = playerRight.maxDura
					playerRight.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerRight.name .. " seharga " .. playerRightCost .. " emas."
					)
					player.money = player.money - playerRightCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerRight.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerMantle ~= nil) then
		if (playerMantle.repairable == 1) then
			if (playerMantle.dura < playerMantle.maxDura) then
				playerMantleCost = math.ceil(((playerMantle.price / playerMantle.maxDura) * (playerMantle.maxDura - playerMantle.dura)) * 0.5)

				if player.money < playerMantleCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerMantle.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerMantleCost .. " emas untuk memperbaikinya."
					)
				else
					playerMantle.dura = playerMantle.maxDura
					playerMantle.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerMantle.name .. " seharga " .. playerMantleCost .. " emas."
					)
					player.money = player.money - playerMantleCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerMantle.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerSubLeft ~= nil) then
		if (playerSubLeft.repairable == 1) then
			if (playerSubLeft.dura < playerSubLeft.maxDura) then
				playerSubLeftCost = math.ceil(((playerSubLeft.price / playerSubLeft.maxDura) * (playerSubLeft.maxDura - playerSubLeft.dura)) * 0.5)

				if player.money < playerSubLeftCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerSubLeft.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerSubLeftCost .. " emas untuk memperbaikinya."
					)
				else
					playerSubLeft.dura = playerSubLeft.maxDura
					playerSubLeft.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerSubLeft.name .. " seharga " .. playerSubLeftCost .. " emas."
					)
					player.money = player.money - playerSubLeftCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerSubLeft.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerSubRight ~= nil) then
		if (playerSubRight.repairable == 1) then
			if (playerSubRight.dura < playerSubRight.maxDura) then
				playerSubRightCost = math.ceil(((playerSubRight.price / playerSubRight.maxDura) * (playerSubRight.maxDura - playerSubRight.dura)) * 0.5)

				if player.money < playerSubRightCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerSubRight.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerSubRightCost .. " emas untuk memperbaikinya."
					)
				else
					playerSubRight.dura = playerSubRight.maxDura
					playerSubRight.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerSubRight.name .. " seharga " .. playerSubRightCost .. " emas."
					)
					player.money = player.money - playerSubRightCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerSubRight.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerNecklace ~= nil) then
		if (playerNecklace.repairable == 1) then
			if (playerNecklace.dura < playerNecklace.maxDura) then
				playerNecklaceCost = math.ceil(((playerNecklace.price / playerNecklace.maxDura) * (playerNecklace.maxDura - playerNecklace.dura)) * 0.5)

				if player.money < playerNecklaceCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerNecklace.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerNecklaceCost .. " emas untuk memperbaikinya."
					)
				else
					playerNecklace.dura = playerNecklace.maxDura
					playerNecklace.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerNecklace.name .. " seharga " .. playerNecklaceCost .. " emas."
					)
					player.money = player.money - playerNecklaceCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerNecklace.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerBoots ~= nil) then
		if (playerBoots.repairable == 1) then
			if (playerBoots.dura < playerBoots.maxDura) then
				playerBootsCost = math.ceil(((playerBoots.price / playerBoots.maxDura) * (playerBoots.maxDura - playerBoots.dura)) * 0.5)

				if player.money < playerBootsCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerBoots.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerBootsCost .. " emas untuk memperbaikinya."
					)
				else
					playerBoots.dura = playerBoots.maxDura
					playerBoots.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerBoots.name .. " seharga " .. playerBootsCost .. " emas."
					)
					player.money = player.money - playerBootsCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerBoots.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	if (playerCoat ~= nil) then
		if (playerCoat.repairable == 1) then
			if (playerCoat.dura < playerCoat.maxDura) then
				playerCoatCost = math.ceil(((playerCoat.price / playerCoat.maxDura) * (playerCoat.maxDura - playerCoat.dura)) * 0.5)

				if player.money < playerCoatCost then
					npc:talk(
						0,
						npc.name .. ": " .. playerCoat.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerCoatCost .. " emas untuk memperbaikinya."
					)
				else
					playerCoat.dura = playerCoat.maxDura
					playerCoat.repairCheck = 0
					npc:talk(
						0,
						npc.name .. ": Sudah kuperbaiki " .. playerCoat.name .. " seharga " .. playerCoatCost .. " emas."
					)
					player.money = player.money - playerCoatCost
					player:sendStatus()
				end
			end
		else
			player:sendMinitext("" .. playerCoat.name .. " bukan barang yang bisa diperbaiki.")
		end
	end

	--- repair items in inventory----

	for x = 0, 52 do
		invItem = player:getInventoryItem(x)
		if invItem ~= nil then
			if (invItem ~= nil and invItem.type > 2 and invItem.type < 17) then
				if invItem.repairable == 1 then
					if (invItem.dura < invItem.maxDura) then
						invCost = math.ceil(((invItem.price / invItem.maxDura) * (invItem.maxDura - invItem.dura)) * 0.5)

						if player.money < invCost then
							npc:talk(
								0,
								npc.name .. ": " .. invItem.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. invCost .. " emas untuk memperbaikinya."
							)
						else
							invItem.dura = invItem.maxDura
							invItem.repairCheck = 0
							npc:talk(
								0,
								npc.name .. ": Sudah kuperbaiki " .. invItem.name .. " seharga " .. invCost .. " emas."
							)
							player.money = player.money - invCost
							player:sendStatus()
						end
					end
				else
					player:sendMinitext("" .. invItem.name .. " bukan barang yang bisa diperbaiki.")
				end
			end
		end
	end

	player:updateInv()
	player:sendStatus()
	player:updateState()
end

function Player.repairItemNoConfirm(player, npc, itemName)
	player:freeAsync()

	local item = Item(itemName)

	if item == nil then
		npc:talk(
			0,
			npc.name .. ": Nah, mana barangnya? Aku tidak melihatnya di mana pun."
		)
		return
	end

	local playerFaceAcc = player:getEquippedItem(EQ_FACEACC)
	local playerHelm = player:getEquippedItem(EQ_HELM)
	local playerCrown = player:getEquippedItem(EQ_CROWN)
	local playerWeapon = player:getEquippedItem(EQ_WEAP)
	local playerArmor = player:getEquippedItem(EQ_ARMOR)
	local playerShield = player:getEquippedItem(EQ_SHIELD)
	local playerLeft = player:getEquippedItem(EQ_LEFT)
	local playerRight = player:getEquippedItem(EQ_RIGHT)
	local playerMantle = player:getEquippedItem(EQ_MANTLE)
	local playerSubLeft = player:getEquippedItem(EQ_SUBLEFT)
	local playerSubRight = player:getEquippedItem(EQ_SUBRIGHT)
	local playerNecklace = player:getEquippedItem(EQ_NECKLACE)
	local playerBoots = player:getEquippedItem(EQ_BOOTS)
	local playerCoat = player:getEquippedItem(EQ_COAT)

	local playerFaceAccCost = 0
	local playerHelmCost = 0
	local playerCrownCost = 0
	local playerWeaponCost = 0
	local playerArmorCost = 0
	local playerShieldCost = 0
	local playerLeftCost = 0
	local playerRightCost = 0
	local playerMantleCost = 0
	local playerSubLeftCost = 0
	local playerSubRightCost = 0
	local playerNecklaceCost = 0
	local playerBootsCost = 0
	local playerCoatCost = 0
	local inventoryCost = 0
	local inventoryItemsCost = 0

	if (playerFaceAcc ~= nil) then
		if (playerFaceAcc.name == item.name) then
			if (playerFaceAcc.repairable == 1) then
				if (playerFaceAcc.dura < playerFaceAcc.maxDura) then
					playerFaceAccCost = math.ceil(((playerFaceAcc.price / playerFaceAcc.maxDura) * (playerFaceAcc.maxDura - playerFaceAcc.dura)) * 0.5)

					if player.money < playerFaceAccCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerFaceAcc.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerFaceAccCost .. " emas untuk memperbaikinya."
						)
					else
						playerFaceAcc.dura = playerFaceAcc.maxDura
						playerFaceAcc.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerFaceAcc.name .. " seharga " .. playerFaceAccCost .. " emas."
						)
						player.money = player.money - playerFaceAccCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerFaceAcc.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end

	if (playerHelm ~= nil) then
		if (playerHelm.name == item.name) then
			if (playerHelm.repairable == 1) then
				if (playerHelm.dura < playerHelm.maxDura) then
					playerHelmCost = math.ceil(((playerHelm.price / playerHelm.maxDura) * (playerHelm.maxDura - playerHelm.dura)) * 0.5)

					if player.money < playerHelmCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerHelm.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerHelmCost .. " emas untuk memperbaikinya."
						)
					else
						playerHelm.dura = playerHelm.maxDura
						playerHelm.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerHelm.name .. " seharga " .. playerHelmCost .. " emas."
						)
						player.money = player.money - playerHelmCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerHelm.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end

	if (playerCrown ~= nil) then
		if (playerCrown.name == item.name) then
			if (playerCrown.repairable == 1) then
				if (playerCrown.dura < playerCrown.maxDura) then
					playerCrownCost = math.ceil(((playerCrown.price / playerCrown.maxDura) * (playerCrown.maxDura - playerCrown.dura)) * 0.5)

					if player.money < playerCrownCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerCrown.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerCrownCost .. " emas untuk memperbaikinya."
						)
					else
						playerCrown.dura = playerCrown.maxDura
						playerCrown.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerCrown.name .. " seharga " .. playerCrownCost .. " emas."
						)
						player.money = player.money - playerCrownCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerCrown.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end
	if (playerWeapon ~= nil) then
		if (playerWeapon.name == item.name) then
			if (playerWeapon.repairable == 1) then
				if (playerWeapon.dura < playerWeapon.maxDura) then
					playerWeaponCost = math.ceil(((playerWeapon.price / playerWeapon.maxDura) * (playerWeapon.maxDura - playerWeapon.dura)) * 0.5)

					if player.money < playerWeaponCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerWeapon.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerWeaponCost .. " emas untuk memperbaikinya."
						)
					else
						playerWeapon.dura = playerWeapon.maxDura
						playerWeapon.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerWeapon.name .. " seharga " .. playerWeaponCost .. " emas."
						)
						player.money = player.money - playerWeaponCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerWeapon.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end

	if (playerArmor ~= nil) then
		if (playerArmor.name == item.name) then
			if (playerArmor.repairable == 1) then
				if (playerArmor.dura < playerArmor.maxDura) then
					playerArmorCost = math.ceil(((playerArmor.price / playerArmor.maxDura) * (playerArmor.maxDura - playerArmor.dura)) * 0.5)

					if player.money < playerArmorCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerArmor.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerArmorCost .. " emas untuk memperbaikinya."
						)
					else
						playerArmor.dura = playerArmor.maxDura
						playerArmor.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerArmor.name .. " seharga " .. playerArmorCost .. " emas."
						)
						player.money = player.money - playerArmorCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerArmor.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end

	if (playerShield ~= nil) then
		if (playerShield.name == item.name) then
			if (playerShield.repairable == 1) then
				if (playerShield.dura < playerShield.maxDura) then
					playerShieldCost = math.ceil(((playerShield.price / playerShield.maxDura) * (playerShield.maxDura - playerShield.dura)) * 0.5)

					if player.money < playerShieldCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerShield.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerShieldCost .. " emas untuk memperbaikinya."
						)
					else
						playerShield.dura = playerShield.maxDura
						playerShield.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerShield.name .. " seharga " .. playerShieldCost .. " emas."
						)
						player.money = player.money - playerShieldCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerShield.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end

	if (playerLeft ~= nil) then
		if (playerLeft.name == item.name) then
			if (playerLeft.repairable == 1) then
				if (playerLeft.dura < playerLeft.maxDura) then
					playerLeftCost = math.ceil(((playerLeft.price / playerLeft.maxDura) * (playerLeft.maxDura - playerLeft.dura)) * 0.5)

					if player.money < playerLeftCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerLeft.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerLeftCost .. " emas untuk memperbaikinya."
						)
					else
						playerLeft.dura = playerLeft.maxDura
						playerLeft.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerLeft.name .. " seharga " .. playerLeftCost .. " emas."
						)
						player.money = player.money - playerLeftCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerLeft.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end

	if (playerRight ~= nil) then
		if (playerRight.name == item.name) then
			if (playerRight.repairable == 1) then
				if (playerRight.dura < playerRight.maxDura) then
					playerRightCost = math.ceil(((playerRight.price / playerRight.maxDura) * (playerRight.maxDura - playerRight.dura)) * 0.5)

					if player.money < playerRightCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerRight.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerRightCost .. " emas untuk memperbaikinya."
						)
					else
						playerRight.dura = playerRight.maxDura
						playerRight.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerRight.name .. " seharga " .. playerRightCost .. " emas."
						)
						player.money = player.money - playerRightCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerRight.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end

	if (playerMantle ~= nil) then
		if (playerMantle.name == item.name) then
			if (playerMantle.repairable == 1) then
				if (playerMantle.dura < playerMantle.maxDura) then
					playerMantleCost = math.ceil(((playerMantle.price / playerMantle.maxDura) * (playerMantle.maxDura - playerMantle.dura)) * 0.5)

					if player.money < playerMantleCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerMantle.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerMantleCost .. " emas untuk memperbaikinya."
						)
					else
						playerMantle.dura = playerMantle.maxDura
						playerMantle.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerMantle.name .. " seharga " .. playerMantleCost .. " emas."
						)
						player.money = player.money - playerMantleCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerMantle.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end

	if (playerSubLeft ~= nil) then
		if (playerSubLeft.name == item.name) then
			if (playerSubLeft.repairable == 1) then
				if (playerSubLeft.dura < playerSubLeft.maxDura) then
					playerSubLeftCost = math.ceil(((playerSubLeft.price / playerSubLeft.maxDura) * (playerSubLeft.maxDura - playerSubLeft.dura)) * 0.5)

					if player.money < playerSubLeftCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerSubLeft.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerSubLeftCost .. " emas untuk memperbaikinya."
						)
					else
						playerSubLeft.dura = playerSubLeft.maxDura
						playerSubLeft.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerSubLeft.name .. " seharga " .. playerSubLeftCost .. " emas."
						)
						player.money = player.money - playerSubLeftCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerSubLeft.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end

	if (playerSubRight ~= nil) then
		if (playerSubRight.name == item.name) then
			if (playerSubRight.repairable == 1) then
				if (playerSubRight.dura < playerSubRight.maxDura) then
					playerSubRightCost = math.ceil(((playerSubRight.price / playerSubRight.maxDura) * (playerSubRight.maxDura - playerSubRight.dura)) * 0.5)

					if player.money < playerSubRightCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerSubRight.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerSubRightCost .. " emas untuk memperbaikinya."
						)
					else
						playerSubRight.dura = playerSubRight.maxDura
						playerSubRight.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerSubRight.name .. " seharga " .. playerSubRightCost .. " emas."
						)
						player.money = player.money - playerSubRightCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerSubRight.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end

	if (playerNecklace ~= nil) then
		if (playerNecklace.name == item.name) then
			if (playerNecklace.repairable == 1) then
				if (playerNecklace.dura < playerNecklace.maxDura) then
					playerNecklaceCost = math.ceil(((playerNecklace.price / playerNecklace.maxDura) * (playerNecklace.maxDura - playerNecklace.dura)) * 0.5)

					if player.money < playerNecklaceCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerNecklace.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerNecklaceCost .. " emas untuk memperbaikinya."
						)
					else
						playerNecklace.dura = playerNecklace.maxDura
						playerNecklace.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerNecklace.name .. " seharga " .. playerNecklaceCost .. " emas."
						)
						player.money = player.money - playerNecklaceCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerNecklace.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end

	if (playerBoots ~= nil) then
		if (playerBoots.name == item.name) then
			if (playerBoots.repairable == 1) then
				if (playerBoots.dura < playerBoots.maxDura) then
					playerBootsCost = math.ceil(((playerBoots.price / playerBoots.maxDura) * (playerBoots.maxDura - playerBoots.dura)) * 0.5)

					if player.money < playerBootsCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerBoots.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerBootsCost .. " emas untuk memperbaikinya."
						)
					else
						playerBoots.dura = playerBoots.maxDura
						playerBoots.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerBoots.name .. " seharga " .. playerBootsCost .. " emas."
						)
						player.money = player.money - playerBootsCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerBoots.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end

	if (playerCoat ~= nil) then
		if (playerCoat.name == item.name) then
			if (playerCoat.repairable == 1) then
				if (playerCoat.dura < playerCoat.maxDura) then
					playerCoatCost = math.ceil(((playerCoat.price / playerCoat.maxDura) * (playerCoat.maxDura - playerCoat.dura)) * 0.5)

					if player.money < playerCoatCost then
						npc:talk(
							0,
							npc.name .. ": " .. playerCoat.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. playerCoatCost .. " emas untuk memperbaikinya."
						)
					else
						playerCoat.dura = playerCoat.maxDura
						playerCoat.repairCheck = 0
						npc:talk(
							0,
							npc.name .. ": Sudah kuperbaiki " .. playerCoat.name .. " seharga " .. playerCoatCost .. " emas."
						)
						player.money = player.money - playerCoatCost
						player:sendStatus()
					end
				end
			else
				player:sendMinitext("" .. playerCoat.name .. " bukan barang yang bisa diperbaiki.")
			end
		end
	end

	--- repair items in inventory----

	for x = 0, 52 do
		invItem = player:getInventoryItem(x)
		if invItem ~= nil then
			if (invItem.type > 2 and invItem.type < 17 and invItem.name == item.name) then
				if invItem.repairable == 1 then
					if (invItem.dura < invItem.maxDura) then
						invCost = math.ceil(((invItem.price / invItem.maxDura) * (invItem.maxDura - invItem.dura)) * 0.5)

						if player.money < invCost then
							npc:talk(
								0,
								npc.name .. ": " .. invItem.name .. " milikmu sudah cukup aus. Aku butuh setidaknya " .. invCost .. " emas untuk memperbaikinya."
							)
						else
							invItem.dura = invItem.maxDura
							invItem.repairCheck = 0
							npc:talk(
								0,
								npc.name .. ": Sudah kuperbaiki " .. invItem.name .. " seharga " .. invCost .. " emas."
							)
							player:removeGold(invCost)
						end
					end
				else
					player:sendMinitext("" .. invItem.name .. " bukan barang yang bisa diperbaiki.")
				end
			end
		end
	end

	player:updateInv()
	player:sendStatus()
	player:updateState()
end

function Player.buyNoConfirm(player, npc, itemName, amount, items, prices, maxAmounts)
	local maxAmount = 0
	if itemName == "coin" or itemName == "coins" then
		return
	end

	if itemName == "" then
		return
	end

	local item = Item(itemName)

	if item == nil then
		return
	end

	local found = false
	local price = 0

	local itemsTemp = {}
	for i = 1, #items do
		table.insert(itemsTemp, Item(items[i]))
	end
	items = itemsTemp

	for i = 1, #items do
		if item.id == items[i].id or itemName == items[i].name then
			found = true
			if prices ~= nil then
				price = prices[i]
			end
			if maxAmounts ~= nil then
				maxAmount = maxAmounts[i]
			end
			break
		end
	end

	if found == false then
		npc:talk(
			0,
			npc.name .. ": Aku tidak menjual " .. item.name .. " di sini, coba cari di tempat lain."
		)
		return
	end

	if price == 0 then
		price = item.price
	end
	if maxAmount == 0 then
		maxAmount = 999
	end

	local invAmount = 0
	for i = 0, 52 do
		local invItem = player:getInventoryItem(i)
		if invItem ~= nil then
			if invItem.id == item.id then
				-- match
				invAmount = invAmount + invItem.amount
			end
		end
	end

	local amountToBuy = 0

	if item.maxAmount > 0 then
		if invAmount > 0 then
			if invAmount + amount >= item.maxAmount then
				amount = item.maxAmount - invAmount
			end
		else
			if amount > item.maxAmount then
				amount = item.maxAmount
			end
		end
	end

	if amount == 0 then
		npc:talk(
			0,
			npc.name .. ": Kau sudah punya jumlah maksimum " .. item.name
		)
		player:sendMinitext(item.name .. ". Kau tidak boleh punya lebih dari (" .. item.maxAmount .. ").")
		return
	end

	if amount > maxAmount then
		npc:talk(
			0,
			npc.name .. ": Maaf, aku hanya bisa menjual (" .. maxAmount .. ") lagi " .. item.name
		)
		return
	end

	if found == true then
		if amount == 1 then
			if player.money < price then
				npc:talk(
					0,
					npc.name .. ": Kepingmu tidak cukup untuk membeli " .. item.name
				)
				return
			end

			if item.yname == "red_potion" then
				if core.gameRegistry["red_potions_available"] - amount < 0 then
					core.gameRegistry["red_potions_available"] = 0
				else
					core.gameRegistry["red_potions_available"] = core.gameRegistry[
						"red_potions_available"
					] - amount
				end
			end

			player:addItem(item.yname, amount)
			player:removeGold(price)
			characterLog.buyItemWrite(player, item, amount, price)
			npc:talk(
				0,
				npc.name .. ": Aku menjual " .. item.name .. " seharga " .. price .. " keping."
			)
		end

		if amount > 1 then
			local totalCost = math.ceil(price * amount)

			if player.money < totalCost then
				npc:talk(
					0,
					npc.name .. ": Kepingmu tidak cukup untuk membeli (" .. amount .. ") " .. item.name
				)
				return
			end

			if item.yname == "red_potion" then
				if core.gameRegistry["red_potions_available"] - amount < 0 then
					core.gameRegistry["red_potions_available"] = 0
				else
					core.gameRegistry["red_potions_available"] = core.gameRegistry[
						"red_potions_available"
					] - amount
				end
			end

			player:addItem(item.yname, amount)
			player:removeGold(totalCost)
			characterLog.buyItemWrite(player, item, amount, totalCost)
			npc:talk(
				0,
				npc.name .. ": Aku menjual (" .. amount .. ") " .. item.name .. " seharga " .. totalCost .. " keping."
			)
		end
	end
end

function Player.sellNoConfirm(player, npc, itemName, amount, items, prices)
	if itemName == "coin" or itemName == "coins" then
		return
	end

	if itemName == "" then
		return
	end

	local item = Item(itemName)

	if item == nil then
		return
	end

	local itemsTemp = {}
	for i = 1, #items do
		table.insert(itemsTemp, Item(items[i]))
	end
	items = itemsTemp

	local price = 0
	local found = false

	for i = 1, #items do
		if item.id == items[i].id or itemName == items[i].name then
			found = true
			if prices ~= nil then
				price = prices[i]
			end
			break
		end
	end

	if found == false then
		npc:talk(
			0,
			npc.name .. ": Aku tidak membeli " .. item.name .. " di sini, coba cari di tempat lain."
		)
		return
	end

	if price == 0 then
		price = item.sell
	end

	for i = 0, 52 do
		local invItem = player:getInventoryItem(i)

		if invItem ~= nil then
			if invItem.yname == item.yname then
				if invItem.dura ~= invItem.maxDura then
					if amount == 1 then
						npc:talk(
							0,
							npc.name .. ": Kau harus memperbaiki " .. item.name .. " sebelum aku mau membelinya."
						)
					elseif amount > 1 then
						npc:talk(
							0,
							npc.name .. ": Kau harus memperbaiki set " .. item.name .. " sebelum aku mau membelinya."
						)
					end
					return
				end
			end
		end
	end

	if found == true then
		if amount == 1 then
			if player:hasItem(item.yname, amount) == true then
				player:removeItem(item.yname, amount, 10)
				player:addGold(price)
				characterLog.sellItemWrite(player, item, amount, price)
				npc:talk(
					0,
					npc.name .. ": Aku membeli " .. item.name .. " seharga " .. price .. " keping."
				)
			else
				npc:talk(0, npc.name .. ": Punyamu tidak cukup.")
			end
		end

		if amount > 1 then
			local totalCost = math.ceil(price * amount)

			if player:hasItem(item.yname, amount) == true then
				player:removeItem(item.yname, amount, 10)
				player:addGold(totalCost)
				characterLog.sellItemWrite(player, item, amount, totalCost)
				npc:talk(
					0,
					npc.name .. ": Aku membeli (" .. amount .. ") " .. item.name .. " seharga " .. totalCost .. " keping."
				)
			else
				npc:talk(0, npc.name .. ": Punyamu tidak cukup.")
			end
		end
	end
end

function Player.depositNoConfirm(player, npc, itemName, amount)
	local inventoryItem
	local found = false
	local slot = 0
	local amountFound = 0

	if itemName == "coin" or itemName == "coins" then
		if player.money < amount then
			npc:talk(0, npc.name .. ": Uangmu tidak cukup.")
			player:talkSelf(
				0,
				"I'll take your money if you want, but getting it back..."
			)
			return
		end

		player:removeGold(amount)
		player.bankMoney = player.bankMoney + amount

		characterLog.depositGoldWrite(player, amount)
		npc:talk(0, npc.name .. ": Kau menitipkan " .. amount .. " keping.")

		return
	end

	if itemName == "" then
		return
	end

	local item = Item(itemName)

	if item == nil then
		return
	end

	for i = 0, 52 do
		inventoryItem = player:getInventoryItem(i)
		if inventoryItem ~= nil then
			if inventoryItem.name == item.name then
				found = true
				slot = i
				break
			end
		end
	end

	local invItemName = inventoryItem.name
	local invItemOwner = inventoryItem.owner
	local invItemEngrave = inventoryItem.realName

	if found == true then
		if player:hasItem(itemName, amount) ~= true then
			npc:talk(0, npc.name .. ": Kau tidak bisa menitipkan lebih dari yang kau punya.")
			return
		end

		if (item.name == "") then
			npc:talk(0, npc.name .. ": Barang itu tidak bisa dititipkan.")
			return false
		end

		if (item.depositable) then
			npc:talk(0, npc.name .. ": Barang itu tidak bisa dititipkan.")
			return false
		end

		if (amount == 0) then
			return
		end

		if (inventoryItem.dura ~= inventoryItem.maxDura) then
			npc:talk(
				0,
				npc.name .. ": Barangmu harus utuh sepenuhnya untuk bisa dititipkan."
			)
			return
		end

		if (inventoryItem.dura == inventoryItem.maxDura) then
			local moneyChoice = ""

			moneyAmount = math.ceil(inventoryItem.sell * 0.10 * amount)

			if (player:hasItem(item.id, amount) == true) then
				if (player.money >= moneyAmount) then
					player:removeGold(moneyAmount)
					player:bankDeposit(
						inventoryItem.id,
						amount,
						inventoryItem.owner,
						inventoryItem.time,
						inventoryItem.realName
					)

					if (amount == 1) then
						player:removeItemSlot((slot), amount, 9)
					else
						player:removeItem(inventoryItem.id, amount, 9)
					end
				else
					npc:talk(
						0,
						npc.name .. ": Maaf, yang kau berikan kurang. Jumlahnya " .. moneyAmount .. " keping."
					)
					return
				end
			else
				npc:talk(
					0,
					npc.name .. ": Kau tidak membawa barang yang ingin kau titipkan padaku."
				)
				return
			end
		else
			npc:talk(
				0,
				npc.name .. ": Aku tidak mau rongsokanmu. Minta pandai besi memperbaikinya."
			)
			return
		end

		characterLog.depositItemWrite(
			player,
			invItemName,
			invItemEngrave,
			amount,
			invItemOwner
		)

		npc:talk(
			0,
			npc.name .. ": Aku ambil " .. Item(itemName).name .. ". " .. amount .. " di antaranya."
		)

		if moneyAmount > 0 then
			npc:talk(0, npc.name .. ": Biayanya " .. moneyAmount .. " keping.")
		end
	end

	if found == false then
		npc:talk(0, npc.name .. ": Nah, mana barangnya?")
	end
end

function Player.withdrawNoConfirm(player, npc, itemName, amount)
	if itemName == "coin" or itemName == "coins" then
		if player.bankMoney < amount then
			npc:talk(
				0,
				npc.name .. ": Kau tidak pernah menitipkan sebanyak itu."
			)
			player:talkSelf(0, "Hey! You little thief.")
			return
		end

		player:addGold(amount)
		player.bankMoney = player.bankMoney - amount
		characterLog.withdrawGoldWrite(player, amount)
		npc:talk(0, npc.name .. ": Ini dia " .. amount .. " keping.")

		return
	end

	if itemName == "" then
		return
	end

	local item = Item(itemName)

	if item == nil then
		--newItem = string.gsub(itemName, "_", " ")
		npc:talk(0, npc.name .. ": Kau tidak memberiku " .. itemName)
		return
	end

	local bankItemTable, bankCountTable, bankOwnerTable, bankEngraveTable, bankTimerTable, bankItemTableNames = player:bankItemsList(0)

	local found = 0
	local counter = 0
	local next = next

	if (#bankItemTable == 0) then
		npc:talk(0, npc.name .. ": Simpananmu sedang kosong.")
		return
	end

	for i = 1, #bankItemTable do
		if (bankItemTable[i] == item.id) then
			found = i
			break
		end
	end

	if (found == 0) then
		npc:talk(0, npc.name .. ": Aku tidak menemukan barang itu di simpananmu.")
		return
	end

	if (Item(bankItemTable[found]).stackAmount > 1) then
		if (amount > bankCountTable[found]) then
			amount = bankCountTable[found]
		end
	else
		amount = 1
	end

	if (amount <= 0) then
		return false
	end

	if Item(bankItemTable[found]).maxAmount > 0 then
		local item = {}
		local hasAmount = 0

		for i = 0, 52 do
			item = player:getInventoryItem(i)
			if item ~= nil then
				if item.id == Item(bankItemTable[found]).id then
					hasAmount = item.amount
					break
				end
			end
		end

		--if hasAmount == 0 then player:popUp("Bug encountered. Contact TKR staff.") return end

		if hasAmount >= Item(bankItemTable[found]).maxAmount or hasAmount + amount > Item(bankItemTable[found]).maxAmount then
			player:sendMinitext("(" .. Item(bankItemTable[found]).name .. "). Kau tidak boleh punya lebih dari (" .. Item(bankItemTable[found]).maxAmount .. ").")
			return
		end
	end

	if (player:hasSpace(
		bankItemTable[found],
		amount,
		bankOwnerTable[found],
		bankEngraveTable[found]
	) ~= true) then
		player:sendMinitext("Ruang di kantongmu tidak cukup untuk mengambil itu")
		return
	end

	local worked = player:addItem(
		bankItemTable[found],
		amount,
		0,
		bankOwnerTable[found],
		bankTimerTable[found],
		bankEngraveTable[found]
	)

	if (worked == true) then
		player:bankWithdraw(
			bankItemTable[found],
			amount,
			bankOwnerTable[found],
			bankTimerTable[found],
			bankEngraveTable[found]
		)
	else
		player:sendMinitext("Tidak bisa mengambil " .. amount .. " " .. Item(bankItemTable[found]).name .. "(s).")
		return false
	end

	characterLog.withdrawItemWrite(
		player,
		Item(bankItemTable[found]).name,
		bankEngraveTable[found],
		amount,
		bankOwnerTable[found]
	)

	if amount == 1 then
		npc:talk(
			0,
			npc.name .. ": Ini dia " .. Item(bankItemTable[found]).name .. "."
		)
	elseif amount > 1 then
		npc:talk(
			0,
			npc.name .. ": Ini dia " .. Item(bankItemTable[found]).name .. " (" .. amount .. ")."
		)
	end
end

function Player.withdrawAllNoConfirm(player, npc, itemName)
	if itemName == "coin" or itemName == "coins" then
		local amount = player.bankMoney

		if amount == 0 then
			npc:talk(0, npc.name .. ": Tidak ada keping yang bisa kau ambil.")
			return
		end

		player.bankMoney = 0
		player:addGold(amount)
		characterLog.withdrawGoldWrite(player, amount)
		npc:talk(0, npc.name .. ": Ini dia " .. amount .. " keping.")

		return
	end

	if itemName == "" then
		return
	end

	local item = Item(itemName)

	if item == nil then
		npc:talk(0, npc.name .. ": Kau tidak memberiku " .. newItem)
		return
	end

	local bankItemTable, bankCountTable, bankOwnerTable, bankEngraveTable, bankTimerTable, bankItemTableNames = player:bankItemsList(0)

	local found = 0
	local amount = 0

	if (bankItemTable == 0) then
		npc:talk(0, npc.name .. ": Simpananmu sedang kosong.")
		return
	end

	for i = 1, #bankItemTable do
		if (bankItemTable[i] == item.id) then
			found = i
			break
		end
	end

	if (found == 0) then
		npc:talk(0, npc.name .. ": Aku tidak menemukan barang itu di simpananmu.")
		return
	end

	if (Item(bankItemTable[found]).stackAmount > 1) then
		amount = bankCountTable[found]
	else
		amount = 1
	end

	if (amount <= 0) then
		return false
	end

	if Item(bankItemTable[found]).maxAmount > 0 then
		local item = {}
		local hasAmount = 0

		for i = 0, 52 do
			item = player:getInventoryItem(i)
			if item ~= nil then
				if item.id == Item(bankItemTable[found]).id then
					hasAmount = item.amount
					break
				end
			end
		end

		--if hasAmount == 0 then player:popUp("Bug encountered. Contact TKR staff.") return end

		if hasAmount >= Item(bankItemTable[found]).maxAmount then
			player:sendMinitext("(" .. Item(bankItemTable[found]).name .. "). Kau tidak boleh punya lebih dari (" .. Item(bankItemTable[found]).maxAmount .. ").")
			return
		end

		if hasAmount + amount > Item(bankItemTable[found]).maxAmount then
			amount = Item(bankItemTable[found]).maxAmount - hasAmount
		end
	end

	if (player:hasSpace(
		bankItemTable[found],
		amount,
		bankOwnerTable[found],
		bankEngraveTable[found]
	) ~= true) then
		player:sendMinitext("Ruang di kantongmu tidak cukup untuk mengambil itu")
		return
	end

	local worked = player:addItem(
		bankItemTable[found],
		amount,
		0,
		bankOwnerTable[found],
		bankTimerTable[found],
		bankEngraveTable[found]
	)

	if (worked == true) then
		player:bankWithdraw(
			bankItemTable[found],
			amount,
			bankOwnerTable[found],
			bankTimerTable[found],
			bankEngraveTable[found]
		)
	else
		player:sendMinitext("Tidak bisa mengambil " .. amount .. " " .. Item(bankItemTable[found]).name .. "(s).")
		return false
	end

	characterLog.withdrawItemWrite(
		player,
		Item(bankItemTable[found]).name,
		bankEngraveTable[found],
		amount,
		bankOwnerTable[found]
	)

	if amount == 1 then
		npc:talk(
			0,
			npc.name .. ": Ini dia " .. Item(bankItemTable[found]).name .. "."
		)
	elseif amount > 1 then
		npc:talk(
			0,
			npc.name .. ": Ini dia " .. Item(bankItemTable[found]).name .. " (" .. amount .. ")."
		)
	end
end

function Player.hasBankItem(player, npc, itemName)
	if itemName == "coin" or itemName == "coins" then
		local amount = player.bankMoney

		if amount == 0 then
			npc:talk(0, npc.name .. ": Kau tidak punya keping.")
			return
		end

		npc:talk(
			0,
			npc.name .. ": Kau menitipkan padaku " .. amount .. " keping."
		)

		return
	end

	if itemName == "" then
		return
	end

	local item = Item(itemName)

	if item == nil then
		--newItem = string.gsub(itemName, "_", " ")
		npc:talk(0, npc.name .. ": Kau tidak memberiku " .. itemName)
		return
	end

	local bankItemTable, bankCountTable, bankOwnerTable, bankEngraveTable, bankTimerTable, bankItemTableNames = player:bankItemsList(0)

	local found = 0
	local counter = 0
	local next = next
	local amount = 0

	for i = 1, #bankItemTable do
		if (bankItemTable[i] == item.id) then
			found = i
			break
		end
	end

	if (found == 0) then
		npc:talk(
			0,
			npc.name .. ": Kau tidak memberiku apa pun.." .. Item(itemName).name .. " untuk kusimpan."
		)
		return
	end

	amount = bankCountTable[found]

	if (amount <= 0) then
		return false
	end

	npc:talk(
		0,
		npc.name .. ": " .. Item(bankItemTable[found]).name .. " -- aku punya " .. Tools.formatNumber(amount) .. " milikmu."
	)
end

function Player.addGold(player, amount)
	if (amount > 0) then
		player.money = player.money + amount
		player:sendStatus()
		player:sendMinitext(amount .. " emas ditambahkan.")
		return true
	elseif (amount < 0) then
		player:sendMinitext("Ini memakai fungsi yang salah, beri tahu GM.")
		return false
	end
end

function Player.removeGold(player, amount)
	if (amount > 0 and player.money < amount) then
		player:sendMinitext("Uangmu tidak cukup.")
		player:sendStatus()
		return false
	elseif (amount > 0 and player.money >= amount) then
		player.money = player.money - amount
		player:sendStatus()
		player:sendMinitext(amount .. " emas diambil.")
		return true
	elseif amount == 0 then
		return true
	elseif (amount < 0) then
		player:sendMinitext("Ini memakai fungsi yang salah, beri tahu GM.")
		return false
	end
end

function Player.getEquippedDura(player, id, slot)
	local item

	if (slot ~= nil) then
		item = player:getEquippedItem(slot)

		if (item ~= nil and item.id == id) then
			return item.dura
		end
	else
		for i = 0, 13 do
			item = player:getEquippedItem(i)

			if (item ~= nil and item.id == id) then
				return item.dura
			end
		end
	end

	return nil
end

function Player.calcDPS(player, times)
	local iterations = ((times * 1000) / ((player.attackSpeed * 1000) / 50))
	local hits = 0
	local damage = 0
	local target = getTargetFacing(player, BL_MOB)

	if (target == nil) then
		target = getTargetFacing(player, BL_PC)
	end

	if (target == nil) then
		return
	end

	for _ = 1, iterations do
		hitCritChance(player, target)

		if (player.critChance > 0) then
			hits = hits + 1
			--swingDamage(player)
			swingDamage(player, target)
			damage = damage + player.damage
		end
	end

	local hitPercentage = Tools.round(hits * 100 / iterations, 3)
	local minutes = Tools.round(times / 60, 2)
	local dps = Tools.formatNumber(Tools.round(damage / times, 0))
	local avg = Tools.formatNumber(Tools.round(damage / hits, 0))

	player:msg(0, "Hits: " .. Tools.formatNumber(hits) .. " (" .. hitPercentage .. "%) -- Minutes: " .. minutes .. " -- Damage: " .. Tools.formatNumber(damage) .. " -- Avg: " .. avg .. " -- DPS: ==> " .. dps .. " <==", player.ID)
end

function Player.canAction(player, dead, mount, disguise)
	if (player.state == -1) then
		player:sendMinitext("Kau tidak bisa melakukan itu sekarang.")
		return false
	end

	if (dead == 1 and player.state == 1) then
		player:sendMinitext("Kau mencoba, tetapi gagal karena kau sudah mati!")
		return false
	end

	if (mount == 1 and player.state == 3) then
		player:sendMinitext("Kau tidak bisa melakukan itu sekarang.")
		return false
	end

	if (disguise == 1 and player.state == 4) then
		player:sendMinitext("Kau tidak bisa melakukan itu sekarang.")
		return false
	elseif (disguise == 2 and (player.state == 4 or player.gfxClone == 1)) then
		player:sendMinitext("Kau tidak bisa melakukan itu sekarang.")
		return false
	end

	return true
end

function Player.canCast(player, dead, mount, disguise)
	if (player.state == -1) then
		player:sendMinitext("Kau tidak bisa merapal itu sekarang.")
		return false
	end

	if (dead == 1 and player.state == 1) then
		player:sendMinitext("Arwah tidak bisa melakukan itu.")
		return false
	end

	if (mount == 1 and player.state == 3) then
		player:sendMinitext("Kau tidak bisa melakukan itu sambil menunggang.")
		return false
	end

	if (disguise == 1 and player.state == 4) then
		player:sendMinitext("Kau tidak bisa melakukan itu dalam wujud berubah.")
		return false
	end

	return true
end

function Player.canPK(player, target)
	if target.blType == BL_MOB then
		return true
	end

	if (target.pvp > 0 or target.PK > 0 or player:getPK(target.ID)) then
		return true
	end

	return false
end

function Player.countryName(player, country)
	if country == nil then
		country = player.country
	end

	if tonumber(country) == 0 then
		return "Neutral"
	end
	if tonumber(country) == 1 then
		return "Kugnae"
	end
	if tonumber(country) == 2 then
		return "Buya"
	end
	if tonumber(country) == 3 then
		return "Nagnang"
	end
end

function Player.lookAtEquippedItem(player, target)
	if (block.blType ~= BL_ITEM) then
		return
	end

	local floorItem = FloorItem(block.ID)

	local icon = 0
	local iconC = 0
	local look = 0
	local lookC = 0

	if (floorItem.customIcon ~= 0 or floorItem.customLook ~= 0) then
		icon = floorItem.customIcon
		iconC = floorItem.customIconC
	else
		icon = block.icon - 49152
		iconC = block.iconC
	end

	local t = {graphic = convertGraphic(icon, "item"), color = iconC}
	player.npcGraphic = t.graphic
	player.npcColor = t.color

	local dam = ""
	local classReq = ""
	local levelReq = "Level " .. block.level .. " Req"
	local dura_pct = "(" .. string.format(
		"%.0f",
		(floorItem.dura / block.maxDura * 100)
	) .. "%)"
	local durability = "Durability: " .. floorItem.dura .. "/" .. block.maxDura .. " " .. dura_pct
	local stats = ""

	--player:talk(0,""..dura_pct)

	if (block.minCritDmg == 0 or block.maxCritDmg == 0) then
		dam = "S " .. block.minDmg .. "m" .. block.maxDmg .. "\n       L " .. block.minDmg .. "m" .. block.maxDmg
	else
		dam = "S " .. block.minDmg .. "m" .. block.maxDmg .. "\n       L " .. block.minCritDmg .. "m" .. block.maxCritDmg
	end

	if (block.baseClass == nil or block.baseClass == 0) then
		classReq = "Any Class"
	elseif (block.baseClass == 1) then
		classReq = "Warrior"
	elseif (block.baseClass == 2) then
		classReq = "Rogue"
	elseif (block.baseClass == 3) then
		classReq = "Mage"
	elseif (block.baseClass == 4) then
		classReq = "Poet"
	end

	-- STATS GENERATION --
	if (block.armor > 0) then
		stats = stats .. "           Armor     +" .. block.armor .. "\n"
	end
	if (block.armor < 0) then
		stats = stats .. "           Armor     -" .. block.armor .. "\n"
	end
	if (block.hit > 0) then
		stats = stats .. "             Hit     +" .. block.hit .. "\n"
	end
	if (block.hit < 0) then
		stats = stats .. "             Hit     -" .. block.hit .. "\n"
	end
	if (block.dam > 0) then
		stats = stats .. "             Dam     +" .. block.dam .. "\n"
	end
	if (block.dam < 0) then
		stats = stats .. "             Dam     -" .. block.dam .. "\n"
	end
	if (block.vita > 0) then
		stats = stats .. "      Vitality increase     +" .. block.vita .. "\n"
	end
	if (block.vita < 0) then
		stats = stats .. "      Vitality increase     " .. block.vita .. "\n"
	end
	if (block.mana > 0) then
		stats = stats .. "      Mana increase     +" .. block.mana .. "\n"
	end
	if (block.mana < 0) then
		stats = stats .. "      Mana increase     " .. block.mana .. "\n"
	end
	if (block.might > 0) then
		stats = stats .. "       Might increase     +" .. block.might .. "\n"
	end
	if (block.might < 0) then
		stats = stats .. "       Might increase     " .. block.might .. "\n"
	end
	if (block.will > 0) then
		stats = stats .. "       Will increase      +" .. block.will .. "\n"
	end
	if (block.will < 0) then
		stats = stats .. "       Will increase      " .. block.will .. "\n"
	end
	if (block.grace > 0) then
		stats = stats .. "       Grace increase     +" .. block.grace .. "\n"
	end
	if (block.grace < 0) then
		stats = stats .. "       Grace increase     " .. block.grace .. "\n"
	end
	if (block.wisdom > 0) then
		stats = stats .. "       Wisdom increase    +" .. block.wisdom .. "\n"
	end
	if (block.wisdom < 0) then
		stats = stats .. "       Wisdom increase    " .. block.wisdom .. "\n"
	end
	if (block.con > 0) then
		stats = stats .. "             Con   +" .. block.con .. "\n"
	end
	if (block.con < 0) then
		stats = stats .. "             Con     " .. block.con .. "\n"
	end
	if (block.protection > 0) then
		stats = stats .. "           Protection +" .. block.protection .. "\n"
	end
	if (block.protection < 0) then
		stats = stats .. "           Protection " .. block.protection .. "\n"
	end

	if (floorItem.customIcon ~= 0 or floorItem.customLook ~= 0) then
		stats = stats .. "\nSkinned: Yes\n"
	else
		stats = stats .. "\nSkinned: No\n"
	end

	------------

	str = block.name .. "\n\n" .. durability .. "\nDamage " .. dam .. "\n" .. stats .. "\n    " .. classReq .. "    " .. levelReq

	player:dialogSeq({t, str}, 0)

	--player:popUp(str)
end

function Player.lookAtItem(player, block)
	if (block.blType ~= BL_ITEM) then
		return
	end

	local floorItem = FloorItem(block.ID)

	local icon = 0
	local iconC = 0
	local look = 0
	local lookC = 0

	if (floorItem.customIcon ~= 0 or floorItem.customLook ~= 0) then
		icon = floorItem.customIcon
		iconC = floorItem.customIconC
	else
		icon = block.icon - 49152
		iconC = block.iconC
	end

	local t = {graphic = convertGraphic(icon, "item"), color = iconC}
	player.npcGraphic = t.graphic
	player.npcColor = t.color

	local dam = ""
	local classReq = ""
	local levelReq = "Level " .. block.level .. " Req"
	local dura_pct = "(" .. string.format(
		"%.0f",
		(floorItem.dura / block.maxDura * 100)
	) .. "%)"
	local durability = "Durability: " .. floorItem.dura .. "/" .. block.maxDura .. " " .. dura_pct
	local stats = ""

	--player:talk(0,""..dura_pct)

	if (block.minCritDmg == 0 or block.maxCritDmg == 0) then
		dam = "S " .. block.minDmg .. "m" .. block.maxDmg .. "\n       L " .. block.minDmg .. "m" .. block.maxDmg
	else
		dam = "S " .. block.minDmg .. "m" .. block.maxDmg .. "\n       L " .. block.minCritDmg .. "m" .. block.maxCritDmg
	end

	if (block.baseClass == nil or block.baseClass == 0) then
		classReq = "Any Class"
	elseif (block.baseClass == 1) then
		classReq = "Warrior"
	elseif (block.baseClass == 2) then
		classReq = "Rogue"
	elseif (block.baseClass == 3) then
		classReq = "Mage"
	elseif (block.baseClass == 4) then
		classReq = "Poet"
	end

	-- STATS GENERATION --
	if (block.armor > 0) then
		stats = stats .. "           Armor     +" .. block.armor .. "\n"
	end
	if (block.armor < 0) then
		stats = stats .. "           Armor     -" .. block.armor .. "\n"
	end
	if (block.hit > 0) then
		stats = stats .. "             Hit     +" .. block.hit .. "\n"
	end
	if (block.hit < 0) then
		stats = stats .. "             Hit     -" .. block.hit .. "\n"
	end
	if (block.dam > 0) then
		stats = stats .. "             Dam     +" .. block.dam .. "\n"
	end
	if (block.dam < 0) then
		stats = stats .. "             Dam     -" .. block.dam .. "\n"
	end
	if (block.vita > 0) then
		stats = stats .. "      Vitality increase     +" .. block.vita .. "\n"
	end
	if (block.vita < 0) then
		stats = stats .. "      Vitality increase     " .. block.vita .. "\n"
	end
	if (block.mana > 0) then
		stats = stats .. "      Mana increase     +" .. block.mana .. "\n"
	end
	if (block.mana < 0) then
		stats = stats .. "      Mana increase     " .. block.mana .. "\n"
	end
	if (block.might > 0) then
		stats = stats .. "       Might increase     +" .. block.might .. "\n"
	end
	if (block.might < 0) then
		stats = stats .. "       Might increase     " .. block.might .. "\n"
	end
	if (block.will > 0) then
		stats = stats .. "       Will increase      +" .. block.will .. "\n"
	end
	if (block.will < 0) then
		stats = stats .. "       Will increase      " .. block.will .. "\n"
	end
	if (block.grace > 0) then
		stats = stats .. "       Grace increase     +" .. block.grace .. "\n"
	end
	if (block.grace < 0) then
		stats = stats .. "       Grace increase     " .. block.grace .. "\n"
	end
	if (block.wisdom > 0) then
		stats = stats .. "       Wisdom increase    +" .. block.wisdom .. "\n"
	end
	if (block.wisdom < 0) then
		stats = stats .. "       Wisdom increase    " .. block.wisdom .. "\n"
	end
	if (block.con > 0) then
		stats = stats .. "             Con   +" .. block.con .. "\n"
	end
	if (block.con < 0) then
		stats = stats .. "             Con     " .. block.con .. "\n"
	end
	if (block.protection > 0) then
		stats = stats .. "           Protection +" .. block.protection .. "\n"
	end
	if (block.protection < 0) then
		stats = stats .. "           Protection " .. block.protection .. "\n"
	end

	if (floorItem.customIcon ~= 0 or floorItem.customLook ~= 0) then
		stats = stats .. "\nSkinned: Yes\n"
	else
		stats = stats .. "\nSkinned: No\n"
	end

	------------

	str = block.name .. "\n\n" .. durability .. "\nDamage " .. dam .. "\n" .. stats .. "\n    " .. classReq .. "    " .. levelReq

	player:dialogSeq({t, str}, 0)

	--player:popUp(str)
end

--GM functions
function Player.gmMsg(player, msg, level)
	local online = {}
	online = player:getUsers()

	if (level == nil) then
		level = 0
	end

	if (msg == nil) then
		return
	end

	for i = 1, #online do
		if (online[i].gmLevel > level) then
			player:msg(12, msg, online[i].ID)
		end
	end
end

function Player.setHighestThreat(player, target)
	local threatcalc = threat.getThreatTable(target)

	local playercheck = threat.getThreatID(target, threatcalc[1])

	if (playercheck.ID ~= player.ID) then
		local threatcalcamount = threatcalc[1] * 1.10
		player:setThreat(target.ID, threatcalcamount)
	end
end

Player.getTotemName = function(player, totem)
	if totem == 0 then
		return "JuJak"
	end
	if totem == 1 then
		return "Baekho"
	end
	if totem == 2 then
		return "Hyun Moo"
	end
	if totem == 3 then
		return "Chung Ryong"
	end
	if totem == 4 then
		return ""
	end
end

Player.isTotemTime = function(player)
	local isTotemTime = false
	local ctime = curTime()

	--  Totem:  (0)JuJak, (1)Baekho, (2)Hyun Moo, (3)Chung Ryong, (4)Nothing
	--[[ Totem Times:
		Chung Ryong (morning) 	  	04-09
		Ju Jak (mid-day) 	  	10-15
		Baekho (evening) 	  	16-21
		Hyun Moo (mid-night) 	  	22-03
	]]
	--

	if (ctime >= 4 and ctime <= 9 and player.totem == 3) then
		-- Chung Ryong
		isTotemTime = true
	elseif (ctime >= 10 and ctime <= 15 and player.totem == 0) then
		-- Ju Jak
		isTotemTime = true
	elseif (ctime >= 16 and ctime <= 21 and player.totem == 1) then
		-- Baekho
		isTotemTime = true
	elseif (((ctime >= 22 and ctime <= 24) or (ctime >= 0 and ctime <= 3)) and player.totem == 2) then
		-- Hyun Moo
		isTotemTime = true
	end

	return isTotemTime
end

function Player.checkTotemTimeXP(player, exp)
	--  Totem:  (0)JuJak, (1)Baekho, (2)Hyun Moo, (3)Chung Ryong, (4)Nothing
	--[[ Totem Times:
		Chung Ryong (morning) 	  	04-09
		Ju Jak (mid-day) 	  	10-15
		Baekho (evening) 	  	16-21
		Hyun Moo (mid-night) 	  	22-03
	]]
	--

	local ctime = curTime()
	local expmulti = 1.05

	if (ctime >= 4 and ctime <= 9 and player.totem == 3) then
		-- Chung Ryong
		exp = round(exp * expmulti)
	elseif (ctime >= 10 and ctime <= 15 and player.totem == 0) then
		-- Ju Jak
		exp = round(exp * expmulti)
	elseif (ctime >= 16 and ctime <= 21 and player.totem == 1) then
		-- Baekho
		exp = round(exp * expmulti)
	elseif (((ctime >= 22 and ctime <= 24) or (ctime >= 0 and ctime <= 3)) and player.totem == 2) then
		-- Hyun Moo
		exp = round(exp * expmulti)
	end

	return exp
end

----------------------------------------------------------------------------------------

function Player.deathDropGold(player)
	local gold = player.money
	local r = math.random(5, 35) * 0.01
	local amount = player.money * r
	local item = 0

	if amount == 1 then
		item = 0
	elseif amount >= 2 and amount <= 99 then
		item = 1
	elseif amount >= 100 and amount <= 999 then
		item = 2
	elseif amount >= 1000 then
		item = 3
	end

	player:removeGold(amount)
	player:dropItem(item, amount)
end

----------Player Death Pile Item Drop------------------------------
function Player.deathPileDrop(player)
	for i = 0, 52 do
		local item = player:getInventoryItem(i)

		if item ~= nil then
			if math.random(1, 2) == 1 then
				-- drop item

				if not item.droppable and item.owner ~= player.ID then
					player:forceDrop(i)
					player:updateInv()
				end
			end
		end
	end

	local groundItemsToCurse = player:getObjectsInCell(
		player.m,
		player.x,
		player.y,
		BL_ITEM
	)
	if #groundItemsToCurse > 0 then
		for i = 1, #groundItemsToCurse do
			groundItemsToCurse[i].looters = {player.ID}
			groundItemsToCurse[i].timer = os.time()
		end

		player:sendMinitext("Barang-barangmu terenggut dari tubuhmu.")
		characterLog.deathPileLog(player)
	end
end

----------------------------------------------------------------------

function Player.deathExpLoss(player, percent)
	local lost = 0

	if player.level < 99 then
		local exp = getXPforLevel(player.baseClass, player.level)
		local exp = exp - getXPforLevel(player.baseClass, player.level - 1)

		lost = math.ceil(exp * 0.20)

		if player.exp - lost < 0 then
			player.exp = 0
		else
			player.exp = player.exp - lost
		end
	elseif player.level == 99 then
		lost = math.ceil(player.exp * percent)

		if player.exp - lost < 0 then
			player.exp = 0
		else
			player.exp = player.exp - lost
		end
	end

	player:calcStat()
	player:sendMinitext("Kau kehilangan " .. Tools.formatNumber(lost) .. " exp!")
end

---------------------------------------------------------------------------------------------

----------Items take damage on death--------------

function Player.deathDuraLoss(player)
	player:deductDuraEquip()
	player:checkInvBod()
end

------------------------------------------------------------------------------------------------

-- -----------------------------------------------------------------------------------------
-- Give experience to a player even if he/she is stacked in the same space as other players
-- -----------------------------------------------------------------------------------------
function Player.giveXPStacked(player, amount, bonus)
	if bonus == nil then
		bonus = 0
	end

	local get = 0

	if player.registry["disableExperienceGain"] == 1 then
		return
	end

	if player.state == 1 then
		player:sendAnimation(246)
		player:sendMinitext("Kau tidak bisa memperoleh exp saat mati!")
		return
	else
		if player.level >= 99 then
			if player.exp + amount + bonus > 4294967295 then
				get = math.abs(math.floor(4294967295 - player.exp))
			else
				get = math.abs(math.floor(amount))
			end
		else
			get = math.abs(math.floor(amount))
		end

		if (player.level >= 5 and player.registry["basic_tutorial_complete"] == 0) then
			player:sendMinitext("Selesaikan tutorialmu untuk melampaui level 5.")
			return
		end

		if (player.level >= 5 and player.class == 0) then
			player:sendMinitext("Kau tidak bisa menaikkan level tanpa memilih jalur lebih dulu.")
			player:sendMinitext("Kunjungi tutor Kerajaanmu, atau tekan F1 lalu pilih \"Choose a path\" dari daftar.")
			return
		end

		if player.exp + get + bonus >= 4294967295 then
			player.exp = 4294967295
			player:sendMinitext("Kau tidak bisa memperoleh pengalaman lagi sampai sebagiannya kau jual.")
			player:sendStatus()
			return
		end

		if get < 0 or bonus < 0 then
			player:sendMinitext("Error contact GM")
		end

		player.exp = player.exp + get + bonus

		player:checkLevel()
		player:sendStatus()

		if get + bonus > 0 then
			player:sendMinitext(Tools.formatNumber(get) .. " (+" .. Tools.formatNumber(bonus) .. " bonus) experience!")
		end
	end
end

-- ---------------------------------------------------------------------------------------------
-- Give experience to a player only if he/she is not stacked in the same space as other players
-- ---------------------------------------------------------------------------------------------
function Player.giveXP(player, amount, bonus)
	local pc = player:getObjectsInCell(player.m, player.x, player.y, BL_PC)

	if (pc ~= nil and #pc > 1) then
		player:sendAnimation(246)
		player:sendMinitext("Kau tidak bisa memperoleh exp saat berdiri di atas pemain lain!")
		return
	end

	player:giveXPStacked(amount, bonus)
end

function Player.recoverDeathPile(player)
	local dptable = {}

	local deathPile = player:getObjectsInArea(BL_ITEM)
	if #deathPile > 0 then
		for i = 1, #deathPile do
			if distanceSquare(player, deathPile[i], 3) then
				if player:isYours(deathPile[i]) then
					table.insert(dptable, deathPile[i])
				end
			end
		end
	end

	if #dptable > 0 then
		for i = 1, #dptable do
			if dptable[i].id <= 3 then
				--If it is coins
				if player:isYours(dptable[i]) then
					dptable[i]:delete()
					player:addGold(dptable[i].amount)
				end
			elseif dptable[i].id > 3 then
				--If it is not coins
				if player:isYours(dptable[i]) then
					characterLog.pickUpWrite(
						player,
						dptable[i],
						dptable[i].amount
					)
					player:pickUp(dptable[i].ID)
				end
			end
		end
		player:sendAction(6, 20)
		player:talk(2, "Aku ambil yang itu.")
	end
end

function Player.isEquipped(player)
	local isEquipped = false

	for i = 0, 14 do
		local equippedItem = player:getEquippedItem(i)

		if equippedItem ~= nil then
			isEquipped = true
		end
	end

	return isEquipped
end

function Player.reset(player)
	player.level = 1
	player.baseGrace = 3
	player.baseWill = 3
	player.baseMight = 3
	player.baseArmor = 99
	player.class = 0
	player.baseHealth = math.random(45, 55)
	player.baseMagic = math.random(32, 36)

	--player.exp = 0
	player:sendStatus()
	player:calcStat()
	player:updateState()
end

function Player.baseClassName(player, class)
	if class == nil then
		class = player.baseClass
	end

	if class == 0 then
		return "Peasant"
	end
	if class == 1 then
		return "Warrior"
	end
	if class == 2 then
		return "Rogue"
	end
	if class == 3 then
		return "Mage"
	end
	if class == 4 then
		return "Poet"
	end
	if class == 5 then
		return "God"
	end
end

function Player.addMana(player, amount)
	if (amount + player.magic > player.maxMagic) then
		player.magic = player.maxMagic
	else
		player.magic = player.magic + amount
	end

	player:sendStatus()
end

function Player.karmaCheck(player, level)
	local level = string.lower(level)

	--player:talk(0,"curr karma: "..player.karma)

	if (level == "snake" and player.karma <= -3) then
		return true
	elseif (level == "rat" and player.karma < 0) then
		return true
	elseif level == "cat" and player.karma >= 0 then
		return true
	elseif level == "squirrel" and player.karma >= 1 then
		return true
	elseif level == "rabbit" and player.karma >= 2 then
		return true
	elseif level == "dog" and player.karma >= 3 then
		return true
	elseif level == "monkey" and player.karma >= 4 then
		return true
	elseif level == "ox" and player.karma >= 6 then
		return true
	elseif level == "bear" and player.karma >= 8 then
		return true
	elseif level == "tiger" and player.karma >= 11 then
		return true
	elseif level == "dragon" and player.karma >= 14 then
		return true
	elseif level == "spirit" and player.karma >= 19 then
		return true
	elseif level == "angel's tear" and player.karma >= 24 then
		return true
	elseif level == "angel" and player.karma >= 30 then
		return true
	end

	return false
end

function Player.karmaLevel(player, karma)
	if karma == nil then
		karma = player.karma
	end

	if (karma <= -3) then
		return "Snake"
	elseif (karma == 0) then
		return "Rat"
	elseif karma >= 0 and karma < 1 then
		return "Cat"
	elseif karma >= 1 and karma < 2 then
		return "Squirrel"
	elseif karma >= 2 and karma < 3 then
		return "Rabbit"
	elseif karma >= 3 and karma < 4 then
		return "Dog"
	elseif karma >= 4 and karma < 6 then
		return "Monkey"
	elseif karma >= 6 and karma < 8 then
		return "Ox"
	elseif karma >= 8 and karma < 11 then
		return "Bear"
	elseif karma >= 11 and karma < 14 then
		return "Tiger"
	elseif karma >= 14 and karma < 19 then
		return "Dragon"
	elseif karma >= 19 and karma < 24 then
		return "Spirit"
	elseif karma >= 24 and karma < 30 then
		return "Angel's tear"
	elseif karma >= 30 then
		return "Angel"
	else
		return "Snake"
	end
end

Player.afkCheckDoorBlock = function(player)
	local isNearWarp = false
	local m = player.m
	local sound = 200
	local anim = 247

	for x = player.x - 1, player.x + 1 do
		for y = player.y - 1, player.y + 1 do
			if getWarp(m, x, y) then
				isNearWarp = true
			end
		end
	end

	if isNearWarp then
		if player.afkTime == 3 then
			if player.registry["door_block_warning"] == 0 then
				player:playSound(sound)
				player:sendAnimation(anim)
				player:sendMinitext("Kau menghalangi jalan. Menyingkirlah, atau kau akan dipindahkan dalam 30 detik.")
				player.registry["door_block_warning"] = 1
			end
		elseif player.afkTime == 5 then
			if player.registry["door_block_warning"] == 1 then
				player:playSound(sound)
				player:sendAnimation(anim)
				player:sendMinitext("Kau menghalangi jalan. Menyingkirlah, atau kau akan dipindahkan dalam 10 detik.")
				player.registry["door_block_warning"] = 2
			end
		elseif player.afkTime == 6 then
			if player.registry["door_block_warning"] == 2 then
				player:playSound(29)
				player:sendMinitext("Kau terlalu dekat dengan portal dan telah dipindahkan ke Penginapan.")
				player:sendAnimation(292)
				player:sendAnimationXY(292, player.x, player.y)
				player.registry["door_block_warning"] = 0

				player:returnFunc()
			end
		elseif player.afkTime == 0 then
			player.registry["door_block_warning"] = 0
		end
	end
end

Player.addKarma = function(player, amount)
	player.karma = player.karma + amount
	local text = ""

	if amount < 1 then
		text = "Your karma rises slightly."
	else
		text = "Your karma has risen."
	end
	player:sendAnimation(49)
	player:sendMinitext(text)
end

Player.removeKarma = function(player, amount)
	player.karma = player.karma - amount
	local text = ""

	if amount < 1 then
		text = "Your karma decreases."
	else
		text = "Your karma has decreased."
	end
	player:sendAnimation(49)
	player:sendMinitext(text)
end

Player.returnFunc = function(player)
	-- used as the global function to control where to warp player upon login

	-- Will check player registry to figure out if player has chosen to live in a city
	-- or Clan (if applicable)
	-- or Pc subpath (if applicable)

	-- the outlying livable areas will start with registry value of 10 and go from there

	if player.registry["basic_tutorial_complete"] ~= 1 then
		player:warp(4711, 3, 3)
	elseif player.clan > 0 and player.registry["home"] == 1 then
		-- is part of clan and has chosen to live in clan
		player:returnToClan()
	elseif player.class >= 10 and player.registry["home"] == 2 then
		-- is part of pc subpath and has chosen to live in subpath
		player:returnToSubpath()
	else
		if player.registry["home"] == 10 then
			-- sanhae
			player:warp(1122, 2, 5)
		elseif player.registry["home"] == 11 then
			--hausson
			player:warp(1027, 4, 5)

			-- haggard mate tavern
		else
			player:returnToInn()

			-- This is the default location (Wilderness by Rotah, or a City Inn)
		end
	end
end

Player.returnToInn = function(player)
	local country = player.country

	player:sendAnimation(11)
	player:sendStatus()
	player:sendAction(6, 35)

	---------------------------------------[ read this ]--------------------

	if country > 3 then
		country = 3
	end

	-- later when more countries are added

	--[[countries   0: neutral, 1: kug, 2: buya, 3: nagnang, 4: han]]
	--

	if (country == 0) then
		-- Neutral (wilderness)
		player:warp(1002, math.random(206, 209), math.random(139, 142))
	elseif (country == 1) then
		-- Kugnae
		local random = math.random(1, 3)
		if random == 1 then
			player:warp(38, 4, 5)

			-- Ginger Tavern
		elseif random == 2 then
			player:warp(37, 4, 5)

			-- Bamboo Tavern
		elseif random == 3 then
			player:warp(2, 4, 5)

			-- Walsuk Tavern
		end
	elseif (country == 2) then
		-- Buya
		local random = math.random(1, 3)
		if random == 1 then
			player:warp(362, 4, 5)

			-- Yunsil Tavern
		elseif random == 2 then
			player:warp(332, 4, 5)

			-- Spring Tavern
		elseif random == 3 then
			player:warp(361, 4, 5)

			-- Pepper Tavern
		end
	elseif (country == 3) then
		-- Nagnang
		local random = math.random(1, 5)

		if random == 1 then
			player:warp(2501, 4, 6)

			-- Tavern of Fire
		elseif random == 2 then
			player:warp(2502, 4, 6)

			-- Tavern of Water
		elseif random == 3 then
			player:warp(2503, 4, 6)

			-- Tavern of Wind
		elseif random == 4 then
			player:warp(2504, 4, 6)

			-- Tavern of Wood
		elseif random == 5 then
			player:warp(2505, 4, 6)

			-- Tavern of Metal
		end
	elseif (country == 4) then
		-- Han
		player:warp(38, 4, 5)

		-- Ginger Tavern
	end
end

Player.returnToSubpath = function(player)
	local className = player.className

	player:sendAnimation(11)
	player:sendStatus()
	player:sendAction(6, 35)

	local m, x, y = 0, 0, 0

	if className == "Barbarian" then
		m, x, y = 3510, math.random(10, 14), math.random(13, 16)
	elseif className == "Merchant" then
		m, x, y = 3520, math.random(8, 14), math.random(9, 12)
	elseif className == "Diviner" then
		m, x, y = 3535, math.random(8, 14), math.random(9, 12)
	elseif className == "Druid" then
		m, x, y = 3630, math.random(8, 14), math.random(9, 12)
	elseif className == "Chongun" then
		m, x, y = 3610, math.random(8, 14), math.random(9, 12)
	elseif className == "Ranger" then
		m, x, y = 3615, math.random(8, 14), math.random(9, 12)
	elseif className == "Geomancer" then
		m, x, y = 3620, math.random(8, 14), math.random(9, 12)
	elseif className == "Monk" then
		m, x, y = 3525, math.random(8, 14), math.random(9, 12)
	elseif className == "Do" then
		m, x, y = 3605, math.random(8, 14), math.random(9, 12)
	elseif className == "Spy" then
		m, x, y = 3515, math.random(8, 14), math.random(9, 12)
	elseif className == "Shaman" then
		m, x, y = 3530, math.random(8, 14), math.random(9, 12)
	elseif className == "Muse" then
		m, x, y = 3625, math.random(8, 14), math.random(9, 12)
	end

	player:warp(m, x, y)
end

Player.returnToClan = function(player)
	local clanName = player.clanName

	player:sendAnimation(11)
	player:sendStatus()
	player:sendAction(6, 35)

	local m, x, y = 0, 0, 0

	if clanName == "Immortals" then
		m, x, y = 4, math.random(3, 8), math.random(5, 8)
	elseif clanName == "Koguryo Army" then
		return
	elseif clanName == "Buya Army" then
		return
	elseif clanName == "Alizarin" then
		m, x, y = 5031, math.random(10, 30), math.random(8, 15)
	elseif clanName == "Bear" then
		m, x, y = 3931, math.random(10, 20), math.random(8, 16)
	elseif clanName == "Covenant" then
		m, x, y = 5051, math.random(10, 30), math.random(8, 15)
	elseif clanName == "Destiny" then
		m, x, y = 4981, math.random(12, 22), math.random(16, 23)
	elseif clanName == "Dharma" then
		m, x, y = 3971, math.random(10, 20), math.random(18, 26)
	elseif clanName == "Elendhirin" then
		m, x, y = 3991, math.random(11, 18), math.random(22, 28)
	elseif clanName == "Enigma" then
		m, x, y = 3951, math.random(11, 19), math.random(19, 26)
	elseif clanName == "Heavens" then
		m, x, y = 3911, math.random(10, 20), math.random(8, 16)
	elseif clanName == "K'urimja" then
		m, x, y = 5021, math.random(8, 25), math.random(19, 27)
	elseif clanName == "Lost Kingdom" then
		m, x, y = 3961, math.random(10, 20), math.random(10, 20)
	elseif clanName == "Oceana" then
		m, x, y = 4011, math.random(7, 25), math.random(2, 5)
	elseif clanName == "Pegasus" then
		m, x, y = 4991, math.random(16, 26), math.random(14, 23)
	elseif clanName == "Phoenix" then
		m, x, y = 3941, math.random(17, 33), math.random(28, 36)
	elseif clanName == "Silla" then
		m, x, y = 5001, math.random(13, 20), math.random(20, 30)
	elseif clanName == "Sun Moon" then
		m, x, y = 3921, math.random(13, 18), math.random(16, 24)
	elseif clanName == "The Forsaken" then
		m, x, y = 3981, math.random(16, 26), math.random(19, 23)
	elseif clanName == "Tiger" then
		m, x, y = 5011, math.random(10, 21), math.random(14, 20)
	elseif clanName == "SanSin" then
		m, x, y = 5041, math.random(3, 13), math.random(11, 16)
	elseif clanName == "Viper" then
		m, x, y = 5061, math.random(12, 22), math.random(20, 26)
	end

	player:warp(m, x, y)
end

function Player.removeDuras(player, type)
	local spells = type

	for i = 1, #spells do
		if player:hasDuration(spells[i]) then
			player:setDuration(spells[i], 0)
		end
	end
end

function Player.checkIfCast(player, spells)
	local cast = false

	for i = 1, #spells do
		if player:hasDuration(spells[i]) then
			cast = true
		end
	end

	return cast
end

function Player.inputLetterCheck(player, input)
	if (input:match("[^%a%s%']")) then
		player:dialog("Kau hanya boleh memasukkan huruf dan spasi.", {})
		return
	end

	return input
end

function Player.inputNumberCheck(player, input)
	if (input == nil) then
		player:dialog("Kau harus memasukkan angka.")
		return
	end

	if (input:match("%D")) then
		player:dialog("Kau hanya boleh memasukkan angka, bukan huruf.", {})
		return
	end

	local number = tonumber(input)

	if (number < 1) then
		player:dialog("Kau harus memasukkan angka positif.", {})
		return
	end

	return number
end

function Player.openCellCheck(player, m, x, y)
	local mob = player:getObjectsInCell(m, x, y, BL_MOB)

	--local npc = player:getObjectsInCell(m,x,y,BL_NPC)
	local pc = player:getObjectsInCell(m, x, y, BL_PC)
	local pass = getPass(m, x, y)

	if #mob == 0 and #pc == 0 and pass == 0 and x >= 0 and x <= getMapXMax(m) and y >= 0 and y <= getMapYMax(m) then
		return true
	else
		return false
	end
end

function Player.getOpenCellApproach(player)
	local m = player.m
	local x = player.x
	local y = player.y

	if player.side == 0 then
		if player:openCellCheck(m, x, y + 1) then
			return m, x, y + 1
		end
		if player:openCellCheck(m, x - 1, y) then
			return m, x - 1, y
		end
		if player:openCellCheck(m, x + 1, y) then
			return m, x + 1, y
		end
		if player:openCellCheck(m, x - 1, y + 1) then
			return m, x - 1, y + 1
		end
		if player:openCellCheck(m, x + 1, y + 1) then
			return m, x + 1, y + 1
		end
		if player:openCellCheck(m, x, y - 1) then
			return m, x, y - 1
		end
	elseif player.side == 1 then
		if player:openCellCheck(m, x - 1, y) then
			return m, x - 1, y
		end
		if player:openCellCheck(m, x, y - 1) then
			return m, x, y - 1
		end
		if player:openCellCheck(m, x, y + 1) then
			return m, x, y + 1
		end
		if player:openCellCheck(m, x - 1, y - 1) then
			return m, x - 1, y - 1
		end
		if player:openCellCheck(m, x - 1, y + 1) then
			return m, x - 1, y + 1
		end
		if player:openCellCheck(m, x + 1, y) then
			return m, x + 1, y
		end
	elseif player.side == 2 then
		-- down
		if player:openCellCheck(m, x, y - 1) then
			return m, x, y - 1
		end
		if player:openCellCheck(m, x + 1, y) then
			return m, x + 1, y
		end
		if player:openCellCheck(m, x - 1, y) then
			return m, x - 1, y
		end
		if player:openCellCheck(m, x + 1, y - 1) then
			return m, x + 1, y - 1
		end
		if player:openCellCheck(m, x - 1, y - 1) then
			return m, x - 1, y - 1
		end
		if player:openCellCheck(m, x, y + 1) then
			return m, x, y + 1
		end
	elseif player.side == 3 then
		if player:openCellCheck(m, x + 1, y) then
			return m, x + 1, y
		end
		if player:openCellCheck(m, x, y + 1) then
			return m, x, y + 1
		end
		if player:openCellCheck(m, x, y - 1) then
			return m, x, y - 1
		end
		if player:openCellCheck(m, x + 1, y + 1) then
			return m, x + 1, y + 1
		end
		if player:openCellCheck(m, x + 1, y - 1) then
			return m, x + 1, y - 1
		end
		if player:openCellCheck(m, x - 1, y) then
			return m, x - 1, y
		end
	end

	return m, x, y
end

function Player.getOpenCellSummon(player)
	local m = player.m
	local x = player.x
	local y = player.y

	if player.side == 0 then
		if player:openCellCheck(m, x, y - 1) then
			return m, x, y - 1
		end
		if player:openCellCheck(m, x - 1, y) then
			return m, x - 1, y
		end
		if player:openCellCheck(m, x + 1, y) then
			return m, x + 1, y
		end
		if player:openCellCheck(m, x - 1, y - 1) then
			return m, x - 1, y - 1
		end
		if player:openCellCheck(m, x + 1, y - 1) then
			return m, x + 1, y - 1
		end
		if player:openCellCheck(m, x, y + 1) then
			return m, x, y + 1
		end
	elseif player.side == 1 then
		-- right
		if player:openCellCheck(m, x + 1, y) then
			return m, x + 1, y
		end
		if player:openCellCheck(m, x, y - 1) then
			return m, x, y - 1
		end
		if player:openCellCheck(m, x, y + 1) then
			return m, x, y + 1
		end
		if player:openCellCheck(m, x + 1, y - 1) then
			return m, x + 1, y - 1
		end
		if player:openCellCheck(m, x + 1, y + 1) then
			return m, x + 1, y + 1
		end
		if player:openCellCheck(m, x - 1, y) then
			return m, x - 1, y
		end
	elseif player.side == 2 then
		-- down
		if player:openCellCheck(m, x, y + 1) then
			return m, x, y + 1
		end
		if player:openCellCheck(m, x + 1, y) then
			return m, x + 1, y
		end
		if player:openCellCheck(m, x - 1, y) then
			return m, x - 1, y
		end
		if player:openCellCheck(m, x + 1, y + 1) then
			return m, x + 1, y + 1
		end
		if player:openCellCheck(m, x - 1, y + 1) then
			return m, x - 1, y + 1
		end
		if player:openCellCheck(m, x, y - 1) then
			return m, x, y - 1
		end
	elseif player.side == 3 then
		-- left
		if player:openCellCheck(m, x - 1, y) then
			return m, x - 1, y
		end
		if player:openCellCheck(m, x, y + 1) then
			return m, x, y + 1
		end
		if player:openCellCheck(m, x, y - 1) then
			return m, x, y - 1
		end
		if player:openCellCheck(m, x - 1, y + 1) then
			return m, x - 1, y + 1
		end
		if player:openCellCheck(m, x - 1, y - 1) then
			return m, x - 1, y - 1
		end
		if player:openCellCheck(m, x + 1, y) then
			return m, x + 1, y
		end
	end

	return m, x, y
end

function Player.retrieveBankItem(player, itemToCheck)
	local itemId = 0
	local amount = 0
	local counter = 0
	local next = next

	local time = 0
	local owner = 0
	local engrave = ""

	local item = Item(itemToCheck)

	if item == nil then
		return nil
	end

	local bankItemTable, bankCountTable, bankOwnerTable, bankEngraveTable, bankTimerTable, bankItemTableNames = player:bankItemsList(0)

	if (next(bankItemTable) == 0) then
		return nil
	end

	for i = 1, #bankItemTable do
		if (bankItemTable[i] == item.id) then
			itemid = bankItemTable[i]
			amount = bankCountTable[i]
			owner = bankOwnerTable[i]
			time = bankTimerTable[i]
			engrave = bankEngraveTable[i]
			break
		end
	end

	return {itemid, amount, owner, time, engrave}
end

function Player.getLooters(player)
	local items = player:getObjectsInCell(player.m, player.x, player.y, BL_ITEM)

	for i = 1, #items do
		for j = 1, 6 do
			player:talk(0, "" .. items[i].looters[j])
		end
	end
end

function Player.canLoot(player, item)
	local pc = player:getObjectsInCell(player.m, player.x, player.y, BL_PC)

	for i = 1, #pc do
		if pc[i].ID ~= player.ID then
			player:sendMinitext("Kau tidak bisa memungut barang saat bertumpuk.")
			return false
		end
	end

	if player.gmLevel ~= 0 then
		return true
	end
	if item.looters[1] == 0 then
		return true
	end

	if item.droppable == 1 then
		return false
	end

	for i = 1, #item.looters do
		if item.looters[i] == player.ID then
			return true
		end
	end

	if item.timer > 0 then
		if os.time() >= item.timer + 300 then
			return true
		end
	end

	player:sendMinitext("Barang itu bukan milikmu.")
	return false
end

function Player.isYours(player, item)
	local pc = player:getObjectsInCell(player.m, player.x, player.y, BL_PC)
	for i = 1, #pc do
		if pc[i].ID ~= player.ID then
			player:sendMinitext("Kau tidak bisa memungut barang saat bertumpuk.")
			return false
		end
	end

	if player.gmLevel ~= 0 then
		return true
	end
	if item.looters[1] == player.ID then
		return true
	end

	if item.droppable == 1 then
		return false
	end

	if item.timer > 0 then
		if os.time() >= item.timer + 300 then
			return true
		end
	end

	player:sendMinitext("Barang itu bukan milikmu.")
	return false
end

function Player.regen(player)
	if player.state == 1 or player.health == 0 then
		return
	end

	if (player.timerTick % 25 == 0 and (string.match(player.mapTitle, "Inn") ~= nil or string.match(
		player.mapTitle,
		"Tavern"
	) ~= nil)) or player.timerTick % 50 == 0 then
		-- 12.5s & inn/tavern or 25s
		local regen = player.healing

		if player:hasDuration("purple_potion") == true then
			regen = regen + 20
		end

		local health = math.ceil(player.maxHealth * 0.02 * (1 + regen / 100))
		local magic = math.ceil(player.maxMagic * 0.02)

		if string.match(player.mapTitle, "Inn") ~= nil or string.match(
			player.mapTitle,
			"Tavern"
		) ~= nil and math.random(1, 20) == 1 then
			player:sendMinitext("Lukamu sembuh cepat selagi kau beristirahat di Penginapan.")
		end

		player.health = player.health + health
		player.magic = player.magic + magic

		if player.health > player.maxHealth then
			player.health = player.maxHealth
		end
		if player.magic > player.maxMagic then
			player.magic = player.maxMagic
		end

		player:sendStatus()
	end
end

function Player.setQuestKillCounts(player, quest, mobs)
	for i = 1, #mobs do
		player.quest[quest .. "_" .. mobs[i] .. "_count"] = player:killCount(mobs[i])
	end
end

function Player.checkQuestKillCount(player, quest, mob)
	local count = player.quest[quest .. "_" .. mob .. "_count"]
	return count
end

function Player.killedEnough(player, mobs, diff)
	local i = 1
	while i < #mobs do
		local mob1kc = player:killCount(mobs[i])
		local mob2kc = player:killCount(mobs[i + 1])

		if mob1kc >= diff and mob2kc >= diff then
			return true
		end

		i = i + 2
	end

	return false
end

function Player.clearQuestKillCounts(player, quest, mobs)
	for i = 1, #mobs do
		player.quest[quest .. "_" .. mobs[i] .. "_count"] = 0
	end
end

function Player.sendFrontAnimation(player, anim)
	local side = player.side

	if side == 0 then
		player:sendAnimationXY(anim, player.x, player.y - 1)
	elseif side == 1 then
		player:sendAnimationXY(anim, player.x + 1, player.y)
	elseif side == 2 then
		player:sendAnimationXY(anim, player.x, player.y + 1)
	elseif side == 3 then
		player:sendAnimationXY(anim, player.x - 1, player.y)
	end
end

function Player.checkOtherSubpathsJoinQuest(player, subpath)
	local subpaths = {
		"barbarian",
		"chongun",
		"do",
		"spy",
		"ranger",
		"merchant",
		"diviner",
		"geomancer",
		"shaman",
		"druid",
		"monk",
		"muse"
	}

	for i = 1, #subpaths do
		if subpaths[i] == subpath then
			table.remove(subpaths, i)
		end
	end

	for i = 1, #subpaths do
		if player.quest[subpaths[i] .. "_subpath_trials"] ~= 0 then
			return true
		end
	end

	return false
end

Player.eventCaveLevelPrompt = function(player, maps, x, y)
	player.npcGraphic = 0
	player.npcColor = 0
	player.dialogType = 0
	player.lastClick = 0

	local level, alternateLevel = player:getEventCaveLevel()

	local opts = {"Yang lebih halus."}
	if alternateLevel ~= nil then
		table.insert(opts, "Yang lebih kasar.")
	end

	if #opts == 1 then
		player:warp(maps[level], x, y)
	else
		local choice = player:menuSeq(
			"Kau melihat dua jalan, mana yang kau pilih?",
			opts,
			{}
		)

		if choice == 1 then
			player:warp(maps[level], x, y)
		elseif choice == 2 then
			player:warp(maps[alternateLevel], x, y)
		end
	end
end

Player.getEventCaveLevel = function(player)
	local level = 1
	local alternateLevel

	if player.level < 69 then
		level = 1
	elseif player.level >= 69 and player.level < 99 then
		level = 2
	elseif player.level >= 99 then
		if player.baseHealth < 160000 and player.baseMagic < 80000 then
			level = 3
			if (160000 - player.baseHealth) < 10000 or (80000 - player.baseMagic) < 5000 then
				alternateLevel = 4
			end
		elseif (player.baseHealth >= 16000 or player.baseMagic >= 80000) and player.baseHealth < 640000 and player.baseMagic < 320000 then
			level = 4
			if (640000 - player.baseHealth) < 10000 or (320000 - player.baseMagic) < 5000 then
				alternateLevel = 5
			end
		elseif player.baseHealth >= 640000 or player.baseMagic >= 320000 then
			level = 5
		end
	end

	return level, alternateLevel
end

Player.alts = function(player, distance)
	local online = player:getUsers()
	local ips = {}
	local names = {}
	local accountid = {}

	if distance == 0 or distance == nil then
		for i = 1, #online do
			if online[i].m == player.m then
				--if distanceSquare(player,online[i],20) then
				table.insert(ips, online[i].ipaddress)
				table.insert(names, online[i].name)
				table.insert(accountid, online[i].actId)
			end
		end
	else
		for i = 1, #online do
			if distanceSquare(player, online[i], distance) then
				table.insert(ips, online[i].ipaddress)
				table.insert(names, online[i].name)
				table.insert(accountid, online[i].actId)
			end
		end
	end

	local sortednames = sort_relative(ips, names)
	local sortedaccountid = sort_relative(ips, accountid)
	local sortedips = sort_relative(ips, ips)

	for i = 1, #ips do
		player:talkSelf(
			0,
			"" .. sortedips[i] .. " " .. sortednames[i] .. " acctid: " .. sortedaccountid[
				i
			]
		)
	end
end
